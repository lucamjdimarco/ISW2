import pandas as pd
import arff
import os

def convert_csv_to_arff(csv_file, arff_file):
    df = pd.read_csv(csv_file)

    df = df.iloc[:, 2:]

    relation_name = os.path.splitext(os.path.basename(csv_file))[0]

    #Mappatura delle colonne a tipi ARFF
    attributes = [
        ('LOC', 'REAL'),
        ('LOC_TOUCHED', 'REAL'),
        ('NUMBER_OF_REVISIONS', 'REAL'),
        ('LOC_ADDED', 'REAL'),
        ('AVG_LOC_ADDED', 'REAL'),
        ('NUMBER_OF_AUTHORS', 'REAL'),
        ('MAX_LOC_ADDED', 'REAL'),
        ('TOTAL_LOC_REMOVED', 'REAL'),
        ('MAX_LOC_REMOVED', 'REAL'),
        ('AVG_LOC_TOUCHED', 'REAL'),
        ('BUGGY', ['NO', 'YES'])
    ]

    #creazione del file ARFF
    arff_data = {
        'description': '',
        'relation': relation_name,  
        'attributes': attributes,
        'data': df.values.tolist()  
    }

    
    with open(arff_file, 'w') as f:
        arff.dump(arff_data, f)
    
    os.remove(csv_file)

def convert_all_csv_in_folder(folder_path):
    for file_name in os.listdir(folder_path):
        if file_name.endswith('.csv'):
            csv_file = os.path.join(folder_path, file_name)
            arff_file = os.path.join(folder_path, file_name[:-4] + '.arff')
            convert_csv_to_arff(csv_file, arff_file)


convert_all_csv_in_folder("fileCSV/training")

convert_all_csv_in_folder("fileCSV/testing")
