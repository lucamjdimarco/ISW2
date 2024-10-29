package git;

import model.Release;
import model.FileJava;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.*;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;

public class GitController {

    private static final Logger logger = Logger.getLogger(GitController.class.getName());
    private static final String JAVAEXT = ".java";

    private GitController() {
        throw new IllegalStateException("Utility class");
    }

    public static void calculateMetric(List<Release> releases, String repoPath) {
        associateCommitsWithReleases(releases, repoPath);
        associateFilesWithCommits(releases, repoPath);
        calculateLOCForReleaseFiles(releases, repoPath);
        calculateNumberOfRevisionsPerFile(releases, repoPath);
        calculateTouchedLOCAndRemovedLOCForReleaseFiles(releases, repoPath);
        calculateAddedLOCAndMaxPerFile(releases, repoPath);
        calculateAvgAddedLOC(releases);
        calculateNumberOfAuthorsPerFile(releases, repoPath);

    }

    public static List<RevCommit> retrieveCommits(String path) {
        Iterable<RevCommit> commits;
        List<RevCommit> commitList=new ArrayList<>();
        try (Git git = Git.open((Path.of(path).toFile()))) {
            commits = git.log().all().call();
            for (RevCommit commit : commits) {
                commitList.add(commit);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoHeadException e) {
            logger.log(SEVERE, "No HEAD found in repository");
        } catch (GitAPIException e) {
            logger.log(SEVERE, "Error while using Git API");
        }
        return commitList;
    }

    //associo i commit alle release
    public static void associateCommitsWithReleases(List<Release> releases, String repoPath) {
        try (Repository repository = Git.open(new File(repoPath)).getRepository()) {
            try (Git git = new Git(repository)) {

                Iterable<RevCommit> commits = git.log().call();

                for (RevCommit commit : commits) {
                    //tempo in ms
                    long commitTime = commit.getCommitTime() * 1000L;

                    //itero sulle release e associo il commit alla prima release valida
                    for (Release release : releases) {
                        long releaseTime = release.getReleaseDate().toInstant(ZoneOffset.UTC).toEpochMilli();

                        if (commitTime <= releaseTime) {
                            release.addCommit(commit);
                            break;
                        }
                    }
                }
            }

            //rimuovo le release senza commit e aggiorno gli indici --> non voglio release vuote (se non ci sono
            //stati commit non ha senso tenerle)
            releases.removeIf(release -> release.getCommits().isEmpty());

            //aggiorno gli indici delle release rimanenti
            for (int i = 0; i < releases.size(); i++) {
                releases.get(i).setIndex(i + 1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static boolean fileExistsInRepository(Repository repository, RevCommit commit, String filePath) throws IOException {
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, commit.getTree())) {
            return treeWalk != null;
        }
    }

    //associo i file ai commit --> l'idea base è tirarmi fuori tutti i file del progetto dalla lista dei file
    //toccati dai commit
    public static void associateFilesWithCommits(List<Release> releases, String repoPath) {
        try (Repository repository = Git.open(new File(repoPath)).getRepository()) {
            for (Release release : releases) {
                associateFilesWithRelease(release, repository);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void associateFilesWithRelease(Release release, Repository repository) {
        for (RevCommit releaseCommit : release.getCommits()) {
            processCommitFiles(repository, release, releaseCommit);
        }
    }

    private static void processCommitFiles(Repository repository, Release release, RevCommit releaseCommit) {
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(releaseCommit.getTree());
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {
                String filePath = treeWalk.getPathString();
                if (isJavaFile(filePath) && fileExistsInRepository(repository, releaseCommit, filePath)) {
                    addFileIfNotExists(release, filePath);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isJavaFile(String filePath) {
        return filePath.endsWith(JAVAEXT);
    }

    private static void addFileIfNotExists(Release release, String filePath) {
        if (!fileExistsInRelease(release, filePath)) {
            FileJava file = new FileJava(filePath);
            release.addFile(file);
        }
    }

    private static boolean fileExistsInRelease(Release release, String filePath) {
        for (FileJava existingFile : release.getFiles()) {
            if (existingFile.getName().equals(filePath)) {
                return true;
            }
        }
        return false;
    }

    /* ------ CALCOLO METRICHE ------ */

    private static AbstractTreeIterator prepareTreeParser(Repository repository, RevCommit commit) throws IOException {
        RevTree tree = commit.getTree();
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (ObjectReader reader = repository.newObjectReader()) {
            treeParser.reset(reader, tree);
        }
        return treeParser;
    }

    public static boolean isCommentOrEmpty(String line) {
        String trimmedLine = line.trim();
        //rimuovo i commenti e le righe vuote
        return trimmedLine.isEmpty() || trimmedLine.startsWith("//") || trimmedLine.startsWith("/*") || trimmedLine.startsWith("*") || trimmedLine.startsWith("*/");
    }

    public static void calculateLOCForReleaseFiles(List<Release> releases, String repoPath) {
        try (Repository repository = Git.open(new File(repoPath)).getRepository()) {
            for (Release release : releases) {
                for (RevCommit commit : release.getCommits()) {
                    List<FileJava> javaFiles = release.getFiles();
                    setFileLOCForCommit(repository, commit, javaFiles);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void setFileLOCForCommit(Repository repo, RevCommit commit, List<FileJava> javaFiles) throws IOException {
        RevTree tree = commit.getTree();

        try (TreeWalk treeWalk = new TreeWalk(repo)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {
                String filePath = treeWalk.getPathString();

                if (isJavaFile(filePath)) {
                    int loc = countLOC(repo, treeWalk);
                    updateFileLOC(javaFiles, filePath, loc);
                }
            }
        }
    }

    private static int countLOC(Repository repo, TreeWalk treeWalk) throws IOException {
        ObjectId objectId = treeWalk.getObjectId(0);
        ObjectLoader loader = repo.open(objectId);

        int numLines = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(loader.openStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCodeLine(line)) {
                    numLines++;
                }
            }
        }
        return numLines;
    }

    private static boolean isCodeLine(String line) {
        String trimmedLine = line.trim();
        return !trimmedLine.isEmpty() && !trimmedLine.startsWith("/*") &&
                !trimmedLine.startsWith("*") && !trimmedLine.startsWith("//");
    }

    private static void updateFileLOC(List<FileJava> javaFiles, String filePath, int loc) {
        for (FileJava javaFile : javaFiles) {
            if (javaFile.getName().equals(filePath)) {
                javaFile.setLoc(loc);
                break;
            }
        }
    }





    //calcolare il numero di revisioni per ogni file Java in ogni release
    public static void calculateNumberOfRevisionsPerFile(List<Release> releases, String repoPath) {
        try (Repository repository = Git.open(new File(repoPath)).getRepository()) {
            try (Git git = new Git(repository)) {
                for (Release release : releases) {
                    Map<String, Integer> fileRevisions = new HashMap<>();

                    for (RevCommit commit : release.getCommits()) {
                        int parentCount = commit.getParentCount();

                        DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                        diffFormatter.setRepository(repository);

                        List<DiffEntry> diffs = parentCount > 0
                                ? diffFormatter.scan(commit.getParent(0), commit)
                                : diffFormatter.scan(null, commit);

                        for (DiffEntry diff : diffs) {
                            String filePath = diff.getNewPath();

                            if (filePath.endsWith(JAVAEXT)) {
                                fileRevisions.put(filePath, fileRevisions.getOrDefault(filePath, 0) + 1);
                            }
                        }
                    }

                    for (Map.Entry<String, Integer> entry : fileRevisions.entrySet()) {
                        String fileName = entry.getKey();
                        int numberOfRevisions = entry.getValue();

                        FileJava javaFile = release.getJavaFileByName(fileName);
                        if (javaFile == null) {
                            javaFile = new FileJava(fileName);
                            release.addFile(javaFile);
                        }
                        javaFile.setNr(numberOfRevisions);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void calculateTouchedLOCAndRemovedLOCForReleaseFiles(List<Release> releases, String repoPath) {
        try (Repository repository = Git.open(new File(repoPath)).getRepository()) {
            for (Release release : releases) {
                Map<String, Integer> totalTouchedLocPerFile = new HashMap<>();
                Map<String, Integer> totalRemovedLocPerFile = new HashMap<>();
                Map<String, Integer> maxRemovedLocPerFile = new HashMap<>();

                for (RevCommit commit : release.getCommits()) {
                    List<FileJava> javaFiles = release.getFiles();
                    calculateTouchedLOCAndRemovedLOCForCommit(repository, commit, javaFiles,
                            totalTouchedLocPerFile, totalRemovedLocPerFile, maxRemovedLocPerFile);
                }

                for (FileJava javaFile : release.getFiles()) {
                    String fileName = javaFile.getName();
                    int revisionCount = javaFile.getNr();

                    if (revisionCount > 0) {
                        int totalTouched = totalTouchedLocPerFile.getOrDefault(fileName, 0);
                        javaFile.setAvgLocTouched(totalTouched / revisionCount);
                    }

                    javaFile.setLocTouched(totalTouchedLocPerFile.getOrDefault(fileName, 0));
                    javaFile.setTotalLocRemoved(totalRemovedLocPerFile.getOrDefault(fileName, 0));
                    javaFile.setMaxLocRemoved(maxRemovedLocPerFile.getOrDefault(fileName, 0));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static List<String> getNewLinesFromCommit(Repository repository, RevCommit commit, String filePath, Edit edit) throws IOException {
        List<String> newLines = new ArrayList<>();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(filePath));

            if (treeWalk.next()) {
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(loader.openStream()))) {
                    String line;
                    int currentLine = 0;
                    while ((line = reader.readLine()) != null) {
                        if (currentLine >= edit.getBeginB() && currentLine < edit.getEndB()) {
                            newLines.add(line);
                        }
                        currentLine++;
                    }
                }
            }
        }
        return newLines;
    }


    public static List<String> getOldLinesFromCommit(Repository repository, RevCommit commit, String filePath, Edit edit) throws IOException {
        List<String> oldLines = new ArrayList<>();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(filePath));

            if (treeWalk.next()) {
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(loader.openStream()))) {
                    String line;
                    int currentLine = 0;
                    while ((line = reader.readLine()) != null) {
                        if (currentLine >= edit.getBeginA() && currentLine < edit.getEndA()) {
                            oldLines.add(line);
                        }
                        currentLine++;
                    }
                }
            }
        }
        return oldLines;
    }


    /* QUI */


    //calcolo delle LOC toccate e rimosse per ogni commit
    private static void calculateTouchedLOCAndRemovedLOCForCommit(Repository repository, RevCommit commit,
                                                                  List<FileJava> javaFiles,
                                                                  Map<String, Integer> totalTouchedLocPerFile,
                                                                  Map<String, Integer> totalRemovedLocPerFile,
                                                                  Map<String, Integer> maxRemovedLocPerFile) throws IOException {
        RevCommit parent = commit.getParentCount() > 0 ? commit.getParent(0) : null;
        if (parent == null) return;

        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDetectRenames(true);

            List<DiffEntry> diffs = diffFormatter.scan(prepareTreeParser(repository, parent), prepareTreeParser(repository, commit));

            for (DiffEntry diff : diffs) {
                String filePath = diff.getNewPath();
                if (filePath.endsWith(JAVAEXT)) {
                    FileJava javaFile = javaFiles.stream().filter(f -> f.getName().equals(filePath)).findFirst().orElse(null);
                    if (javaFile != null) {
                        int addedLines = 0;
                        int removedLines = 0;

                        for (Edit edit : diffFormatter.toFileHeader(diff).toEditList()) {
                            addedLines += getNewLinesFromCommit(repository, commit, filePath, edit)
                                    .stream().filter(line -> !isCommentOrEmpty(line)).count();
                            removedLines += getOldLinesFromCommit(repository, parent, filePath, edit)
                                    .stream().filter(line -> !isCommentOrEmpty(line)).count();
                        }

                        int locTouched = addedLines + removedLines;
                        totalTouchedLocPerFile.merge(filePath, locTouched, Integer::sum);
                        totalRemovedLocPerFile.merge(filePath, removedLines, Integer::sum);
                        maxRemovedLocPerFile.merge(filePath, removedLines, Math::max);
                    }
                }
            }
        }
    }



    public static void calculateAddedLOCAndMaxPerFile(List<Release> releases, String repoPath) {
        try (Repository repository = Git.open(new File(repoPath)).getRepository()) {
            try (Git git = new Git(repository)) {
                for (Release release : releases) {
                    Map<String, Integer> maxLocAddedPerFile = new HashMap<>();

                    for (RevCommit commit : release.getCommits()) {
                        RevCommit parent = commit.getParentCount() > 0 ? commit.getParent(0) : null;
                        if (parent != null) {
                            calculateAddedLOCAndMaxForCommit(repository, commit, parent, release.getFiles(), maxLocAddedPerFile, git);
                        } else {
                            calculateAddedLOCAndMaxForFirstCommit(repository, commit, release.getFiles(), maxLocAddedPerFile);
                        }
                    }

                    maxLocAddedPerFile.forEach((fileName, maxLocAdded) -> {
                        FileJava javaFile = release.getJavaFileByName(fileName);
                        if (javaFile == null) {
                            javaFile = new FileJava(fileName);
                            release.addFile(javaFile);
                        }
                        javaFile.setMaxLocAdded(maxLocAdded);
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }






    //calcolo LOC aggiunte e MAX LOX per commit
    private static void calculateAddedLOCAndMaxForCommit(Repository repository, RevCommit commit, RevCommit parent,
                                                         List<FileJava> javaFiles, Map<String, Integer> maxLocAddedPerFile, Git git) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDetectRenames(true);

            List<DiffEntry> diffs = diffFormatter.scan(prepareTreeParser(repository, parent), prepareTreeParser(repository, commit));

            for (DiffEntry diff : diffs) {
                String filePath = diff.getNewPath();
                if (filePath.endsWith(JAVAEXT)) {
                    int addedLines = 0;

                    for (Edit edit : diffFormatter.toFileHeader(diff).toEditList()) {
                        addedLines += getNewLinesFromCommit(repository, commit, filePath, edit)
                                .stream().filter(line -> !isCommentOrEmpty(line)).count();
                    }

                    int finalAddedLines = addedLines;
                    javaFiles.stream()
                            .filter(javaFile -> javaFile.getName().equals(filePath))
                            .forEach(javaFile -> {
                                javaFile.setLocAdded(javaFile.getLocAdded() + finalAddedLines);
                                maxLocAddedPerFile.merge(filePath, finalAddedLines, Math::max);
                            });
                }
            }
        }
    }


    //se è il primissimo commit --> non ho parent
    private static void calculateAddedLOCAndMaxForFirstCommit(Repository repository, RevCommit commit,
                                                              List<FileJava> javaFiles, Map<String, Integer> maxLocAddedPerFile) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);

            List<DiffEntry> diffs = diffFormatter.scan(null, commit);

            for (DiffEntry diff : diffs) {
                String filePath = diff.getNewPath();
                if (filePath.endsWith(JAVAEXT)) {
                    int addedLines = 0;

                    ObjectId objectId = diff.getNewId().toObjectId();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(repository.open(objectId).openStream()))) {
                        addedLines = (int) reader.lines()
                                .filter(line -> !isCommentOrEmpty(line.trim()))
                                .count();
                    }

                    int finalAddedLines = addedLines;
                    javaFiles.stream()
                            .filter(javaFile -> javaFile.getName().equals(filePath))
                            .forEach(javaFile -> {
                                javaFile.setLocAdded(javaFile.getLocAdded() + finalAddedLines);
                                maxLocAddedPerFile.merge(filePath, finalAddedLines, Math::max);
                            });
                }
            }
        }
    }


    public static void calculateAvgAddedLOC(List<Release> releases) {
        for (Release release : releases) {
            release.getFiles().forEach(javaFile -> {
                int totalAddedLOC = javaFile.getLocAdded();
                int numberOfRevisions = javaFile.getNr();

                double avgAddedLOC = (numberOfRevisions > 0) ? (double) totalAddedLOC / numberOfRevisions : 0;
                javaFile.setAvgLocAdded(avgAddedLOC);
            });
        }
    }



    public static void calculateNumberOfAuthorsPerFile(List<Release> releases, String repoPath) {
        try (Repository repository = Git.open(new File(repoPath)).getRepository()) {
            try (Git git = new Git(repository)) {
                for (Release release : releases) {
                    Map<String, Set<String>> fileAuthors = new HashMap<>();

                    for (RevCommit commit : release.getCommits()) {
                        String author = commit.getAuthorIdent().getName();

                        int parentCount = commit.getParentCount();

                        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                            diffFormatter.setRepository(repository);

                            List<DiffEntry> diffs = (parentCount > 0)
                                    ? diffFormatter.scan(commit.getParent(0), commit)
                                    : diffFormatter.scan(null, commit);

                            diffs.stream()
                                    .filter(diff -> diff.getNewPath().endsWith(".java"))
                                    .forEach(diff -> fileAuthors.computeIfAbsent(diff.getNewPath(), k -> new HashSet<>()).add(author));
                        }
                    }

                    fileAuthors.forEach((fileName, authors) -> {
                        FileJava javaFile = release.getJavaFileByName(fileName);
                        if (javaFile == null) {
                            javaFile = new FileJava(fileName);
                            release.addFile(javaFile);
                        }
                        javaFile.setNauth(authors.size());
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }








}
