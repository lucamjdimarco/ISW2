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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.*;

public class GitController {

    private GitController() {
        throw new IllegalStateException("Utility class");
    }

    public static void calculateMetric(List<Release> releases, String repoPath) {
        associateCommitsWithReleases(releases, repoPath);
        associateFilesWithCommits(releases, repoPath);
        /* ------- */
        calculateLOCForReleaseFiles(releases, repoPath);
        /* ------- */
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
            throw new RuntimeException(e);
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
        return commitList;
    }

    //associo i commit alle release
    public static void associateCommitsWithReleases(List<Release> releases, String repoPath) {
        try (Repository repository = Git.open(new File(repoPath)).getRepository()) {
            //l'istanza di Git permette di accedere a tutti i comandi di Git
            try (Git git = new Git(repository)) {
                //ottengo tutti i commit dal repository --> l'Iterable rappresenta una sequenza di commit
                Iterable<RevCommit> commits = git.log().call();

                for (RevCommit commit : commits) {
                    for (Release release : releases) {
                        //se il commit è stato effettuato prima della data della release
                        if (commit.getCommitTime() * 1000L <= release.getReleaseDate().toInstant(ZoneOffset.UTC).toEpochMilli()) {
                            //associo il commit alla release
                            release.addCommit(commit);
                        }
                    }
                }
            }

            //associo i file ad ogni releases
            //associateFilesWithCommits(releases, repoPath);
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
            //try (Git git = new Git(repository)) {
                //ottengo tutti i commit dal repository
                //Iterable<RevCommit> commits = git.log().call();

                //for (RevCommit commit : commits) {
            for (Release release : releases) {
                for (RevCommit releaseCommit : release.getCommits()) {
                    //se il commit è incluso in quella release
                    //if (commit.getId().equals(releaseCommit.getId())) {
                        //se il commit è associato alla release
                        //inizializzo un nuovo TreeWalk per navigare l'albero del commit
                    try (TreeWalk treeWalk = new TreeWalk(repository)) {
                        //treeWalk.addTree(commit.getTree());
                        treeWalk.addTree(releaseCommit.getTree());
                        treeWalk.setRecursive(true);

                        while (treeWalk.next()) {
                            //per ogni file, ottengo il nome
                            String filePath = treeWalk.getPathString();
                            //controllo se è .java
                            //if (filePath.endsWith(".java")) {
                            if (filePath.endsWith(".java") && fileExistsInRepository(repository, releaseCommit, filePath)) {
                                //controllo se è già presente nella lista dei file
                                //non voglio duplicati
                                boolean exists = false;
                                for (FileJava existingFile : release.getFiles()) {
                                    if (existingFile.getName().equals(filePath)) {
                                        exists = true;
                                        break; //esco se trovo un duplicato
                                    }
                                }
                                //se non esiste, lo aggiungo creando un nuovo oggetto FileJava
                                if (!exists) {
                                    FileJava file = new FileJava(filePath);
                                    release.addFile(file);
                                }
                            }
                        }
                    }
                    //}
                }
            }
                //}
            //}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void calculateLOCForReleaseFiles(List<Release> releases, String repoPath) {
        try (Repository repository = Git.open(new File(repoPath)).getRepository()) {
            for (Release release : releases) {
                for (RevCommit commit : release.getCommits()) {
                    List<FileJava> javaFiles = release.getFiles();

                    //calcolo numero di LOC per ogni file Java
                    setFileLOCForCommit(repository, commit, javaFiles);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //calcolo in maniera cumulativa i LOC totali per ogni file Java per ogni release
    //i LOC sono calcolati tra una release e l'altra
    private static void setFileLOCForCommit(Repository repo, RevCommit commit, List<FileJava> javaFiles) throws IOException {

        RevTree tree = commit.getTree();

        for (FileJava javaFile : javaFiles) {
            String filePath = javaFile.getName();

            TreeWalk treeWalk = new TreeWalk(repo);
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(filePath));

            if (!treeWalk.next()) {
                continue;
            }

            ObjectId objectId = treeWalk.getObjectId(0);
            ObjectLoader loader = repo.open(objectId);

            //legge il contenuto del file e conta le LOC
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(loader.openStream()))) {
                String line;
                int numLines = 0;
                while ((line = reader.readLine()) != null) {
                    String trimmedLine = line.trim();
                    if (!trimmedLine.isEmpty() && !(trimmedLine.startsWith("/*") ||
                            trimmedLine.startsWith("*") || trimmedLine.startsWith("//"))) {
                        numLines++;
                    }
                }
                //aggiorna le LOC totali nel JavaFile
                //javaFile.setLoc(javaFile.getLoc() + numLines);
                javaFile.setLoc(numLines);
            }
        }
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, RevCommit commit) throws IOException {
        RevTree tree = commit.getTree();
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (ObjectReader reader = repository.newObjectReader()) {
            treeParser.reset(reader, tree);
        }
        return treeParser;
    }

    //calcolare il numero di revisioni per ogni file Java in ogni release
    public static void calculateNumberOfRevisionsPerFile(List<Release> releases, String repoPath) {
        try (Repository repository = Git.open(new File(repoPath)).getRepository()) {
            try (Git git = new Git(repository)) {
                for (Release release : releases) {
                    Map<String, Integer> fileRevisions = new HashMap<>(); //mappa per tracciare numero di revisioni per file Java

                    for (RevCommit commit : release.getCommits()) {
                        int parentCount = commit.getParentCount();

                        //per ogni commit itero sui file modificati
                        if (parentCount > 0) {
                            DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                            diffFormatter.setRepository(repository);

                            //esegue diff tra commit attuale e primo genitore per capire quali file sono stati modificati
                            List<DiffEntry> diffs = diffFormatter.scan(commit.getParent(0), commit);

                            //per ogni file .java modificato
                            for (DiffEntry diff : diffs) {
                                //ottengo il suo nome
                                String filePath = diff.getNewPath();

                                // Considera solo i file Java
                                if (filePath.endsWith(".java")) {
                                    //incremento il valore del contatore --> ha avuto una revisione
                                    fileRevisions.put(filePath, fileRevisions.getOrDefault(filePath, 0) + 1);
                                }
                            }
                        } else {
                            //quando ho un commit senza genitore, controllo solo i file modificati
                            DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                            diffFormatter.setRepository(repository);

                            //uso un albero vuoto per il confronto --> non ho genitori
                            List<DiffEntry> diffs = diffFormatter.scan(null, commit);

                            for (DiffEntry diff : diffs) {
                                String filePath = diff.getNewPath();

                                if (filePath.endsWith(".java")) {
                                    fileRevisions.put(filePath, fileRevisions.getOrDefault(filePath, 0) + 1);
                                }
                            }
                        }
                    }

                    //per ogni file Java, aggiorno il numero di revisioni per ogni release
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
                            totalTouchedLocPerFile,
                            totalRemovedLocPerFile,
                            maxRemovedLocPerFile);
                }

                //aggiorno i valori per ogni file
                for (FileJava javaFile : release.getFiles()) {
                    String fileName = javaFile.getName();

                    int revisionCount = javaFile.getNr(); //ottengo le revisioni prima calcolate
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

                BufferedReader reader = new BufferedReader(new InputStreamReader(loader.openStream()));
                String line;
                int currentLine = 0;
                while ((line = reader.readLine()) != null) {
                    // Prendi solo le righe modificate
                    if (currentLine >= edit.getBeginB() && currentLine < edit.getEndB()) {
                        newLines.add(line);
                    }
                    currentLine++;
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

                BufferedReader reader = new BufferedReader(new InputStreamReader(loader.openStream()));
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
        return oldLines;
    }


    public static boolean isCommentOrEmpty(String line) {
        String trimmedLine = line.trim();
        // Controlla commenti singoli e multi-linea
        return trimmedLine.isEmpty() || trimmedLine.startsWith("//") || trimmedLine.startsWith("/*") || trimmedLine.startsWith("*") || trimmedLine.startsWith("*/");
    }

    //calcolo delle LOC toccate e rimosse per ogni commit
    private static void calculateTouchedLOCAndRemovedLOCForCommit(Repository repository, RevCommit commit,
                                                                  List<FileJava> javaFiles,
                                                                  Map<String, Integer> totalTouchedLocPerFile,
                                                                  Map<String, Integer> totalRemovedLocPerFile,
                                                                  Map<String, Integer> maxRemovedLocPerFile)
            throws IOException {
        RevCommit parent = commit.getParentCount() > 0 ? commit.getParent(0) : null;

        if (parent == null) {
            return;
        }

        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDetectRenames(true);

            AbstractTreeIterator parentTreeParser = prepareTreeParser(repository, parent);
            AbstractTreeIterator commitTreeParser = prepareTreeParser(repository, commit);

            List<DiffEntry> diffs = diffFormatter.scan(parentTreeParser, commitTreeParser);

            for (DiffEntry diff : diffs) {
                String filePath = diff.getNewPath();

                if (filePath.endsWith(".java")) {
                    for (FileJava javaFile : javaFiles) {
                        if (javaFile.getName().equals(filePath)) {
                            int addedLines = 0;
                            int removedLines = 0;

                            //---------------------------------------------
                            //---------------------------------------------

                            for (Edit edit : diffFormatter.toFileHeader(diff).toEditList()) {
                                /*addedLines += edit.getEndB() - edit.getBeginB();
                                removedLines += edit.getEndA() - edit.getBeginA();*/
                                List<String> newLines = getNewLinesFromCommit(repository, commit, filePath, edit);

                                // Aggiungi solo le righe non commento o non vuote
                                for (String line : newLines) {
                                    if (!isCommentOrEmpty(line)) {
                                        addedLines++;
                                    }
                                }

                                List<String> oldLines = getOldLinesFromCommit(repository, parent, filePath, edit);

                                // Conta solo le righe rimosse che non sono commenti o vuote
                                for (String line : oldLines) {
                                    if (!isCommentOrEmpty(line)) {
                                        removedLines++;
                                    }
                                }
                            }

                            //---------------------------------------------
                            //---------------------------------------------

                            int locTouched = addedLines + removedLines;
                            totalTouchedLocPerFile.put(filePath, totalTouchedLocPerFile.getOrDefault(filePath, 0) + locTouched);
                            totalRemovedLocPerFile.put(filePath, totalRemovedLocPerFile.getOrDefault(filePath, 0) + removedLines);

                            maxRemovedLocPerFile.put(filePath, Math.max(maxRemovedLocPerFile.getOrDefault(filePath, 0), removedLines));
                        }
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
                            //calculateAddedLOCAndMaxForCommit(repository, commit, parent, release.getFiles(), maxLocAddedPerFile);
                            calculateAddedLOCAndMaxForCommit(repository, commit, parent, release.getFiles(), maxLocAddedPerFile, git);
                        } else {
                            calculateAddedLOCAndMaxForFirstCommit(repository, commit, release.getFiles(), maxLocAddedPerFile);
                        }
                    }

                    for (Map.Entry<String, Integer> entry : maxLocAddedPerFile.entrySet()) {
                        String fileName = entry.getKey();
                        int maxLocAdded = entry.getValue();

                        FileJava javaFile = release.getJavaFileByName(fileName);
                        if (javaFile == null) {
                            javaFile = new FileJava(fileName);
                            release.addFile(javaFile);
                        }
                        javaFile.setMaxLocAdded(maxLocAdded);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }





    //calcolo LOC aggiunte e MAX LOX per commit
    private static void calculateAddedLOCAndMaxForCommit(Repository repository, RevCommit commit, RevCommit parent, List<FileJava> javaFiles, Map<String, Integer> maxLocAddedPerFile, Git git) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDetectRenames(true);

            AbstractTreeIterator parentTreeParser = prepareTreeParser(repository, parent);
            AbstractTreeIterator commitTreeParser = prepareTreeParser(repository, commit);

            List<DiffEntry> diffs = diffFormatter.scan(parentTreeParser, commitTreeParser);

            for (DiffEntry diff : diffs) {
                String filePath = diff.getNewPath();

                /* ---------------------------------------------*/
                /*if (filePath.endsWith(".java")) {
                    int addedLines = 0;

                    for (Edit edit : diffFormatter.toFileHeader(diff).toEditList()) {
                        addedLines += edit.getEndB() - edit.getBeginB();
                    }

                    for (FileJava javaFile : javaFiles) {
                        if (javaFile.getName().equals(filePath)) {

                            javaFile.setLocAdded(javaFile.getLocAdded() + addedLines);
                            maxLocAddedPerFile.put(filePath, Math.max(maxLocAddedPerFile.getOrDefault(filePath, 0), addedLines));
                        }
                    }
                }*/
                /* ---------------------------------------------*/
                /* ---------------------------------------------*/
                if (filePath.endsWith(".java")) {
                    int addedLines = 0;

                    for (Edit edit : diffFormatter.toFileHeader(diff).toEditList()) {
                        // Ottieni le righe aggiunte dal commit
                        List<String> newLines = getNewLinesFromCommit(repository, commit, filePath, edit);

                        // Filtra le righe che non sono commenti o vuote
                        for (String line : newLines) {
                            if (!isCommentOrEmpty(line)) {
                                addedLines++;
                            }
                        }
                    }

                    for (FileJava javaFile : javaFiles) {
                        if (javaFile.getName().equals(filePath)) {
                            javaFile.setLocAdded(javaFile.getLocAdded() + addedLines);
                            maxLocAddedPerFile.put(filePath, Math.max(maxLocAddedPerFile.getOrDefault(filePath, 0), addedLines));
                        }
                    }
                }

            }
        }
    }

    //se è il primissimo commit --> non ho parent
    private static void calculateAddedLOCAndMaxForFirstCommit(Repository repository, RevCommit commit, List<FileJava> javaFiles, Map<String, Integer> maxLocAddedPerFile) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);

            List<DiffEntry> diffs = diffFormatter.scan(null, commit);

            for (DiffEntry diff : diffs) {
                String filePath = diff.getNewPath();

                if (filePath.endsWith(".java")) {
                    int addedLines = 0;

                    ObjectId objectId = diff.getNewId().toObjectId();
                    ObjectLoader loader = repository.open(objectId);

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(loader.openStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            /*if (!line.trim().isEmpty()) {
                                addedLines++;
                            }*/
                            String trimmedLine=line.trim();
                            if (!trimmedLine.isEmpty() && !(trimmedLine.startsWith("/*") ||
                                    trimmedLine.startsWith("*") || trimmedLine.startsWith("//"))) {
                                addedLines++;
                            }
                        }
                    }

                    for (FileJava javaFile : javaFiles) {
                        if (javaFile.getName().equals(filePath)) {
                            javaFile.setLocAdded(javaFile.getLocAdded() + addedLines);
                            maxLocAddedPerFile.put(filePath, Math.max(maxLocAddedPerFile.getOrDefault(filePath, 0), addedLines));
                        }
                    }
                }
            }
        }
    }

    public static void calculateAvgAddedLOC(List<Release> releases) {
        for (Release release : releases) {
            for (FileJava javaFile : release.getFiles()) {
                int totalAddedLOC = javaFile.getLocAdded(); //LOC aggiunte totali
                int numberOfRevisions = javaFile.getNr(); //numero di revisioni

                if (numberOfRevisions > 0) {
                    double avgAddedLOC = (double) totalAddedLOC / numberOfRevisions;
                    javaFile.setAvgLocAdded(avgAddedLOC);
                } else {
                    javaFile.setAvgLocAdded(0); //se non ci sono revisioni
                }
            }
        }
    }


    public static void calculateNumberOfAuthorsPerFile(List<Release> releases, String repoPath) {
        try (Repository repository = Git.open(new File(repoPath)).getRepository()) {
            try (Git git = new Git(repository)) {
                for (Release release : releases) {
                    //la mappa traccia gli autori del file java (per evitare duplicati)
                    Map<String, Set<String>> fileAuthors = new HashMap<>();

                    for (RevCommit commit : release.getCommits()) {
                        //viene recuperato l'autore del commit
                        String author = commit.getAuthorIdent().getName();

                        int parentCount = commit.getParentCount();

                        if (parentCount > 0) {
                            DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                            diffFormatter.setRepository(repository);

                            List<DiffEntry> diffs = diffFormatter.scan(commit.getParent(0), commit);

                            for (DiffEntry diff : diffs) {
                                String filePath = diff.getNewPath();

                                if (filePath.endsWith(".java")) {
                                    //aggiunta dell'autore
                                    fileAuthors.computeIfAbsent(filePath, k -> new HashSet<>()).add(author);
                                }
                            }
                        } else {
                            //senza parent, confronto con albero vuoto
                            DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                            diffFormatter.setRepository(repository);

                            List<DiffEntry> diffs = diffFormatter.scan(null, commit);

                            for (DiffEntry diff : diffs) {
                                String filePath = diff.getNewPath();

                                if (filePath.endsWith(".java")) {
                                    fileAuthors.computeIfAbsent(filePath, k -> new HashSet<>()).add(author);
                                }
                            }
                        }
                    }


                    for (Map.Entry<String, Set<String>> entry : fileAuthors.entrySet()) {
                        String fileName = entry.getKey();
                        int numberOfAuthors = entry.getValue().size(); // Conteggio degli autori unici

                        FileJava javaFile = release.getJavaFileByName(fileName);
                        if (javaFile == null) {
                            javaFile = new FileJava(fileName);
                            release.addFile(javaFile);
                        }
                        javaFile.setNauth(numberOfAuthors);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }






}
