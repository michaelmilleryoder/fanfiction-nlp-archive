import pandas as pd
import glob
import time
from collections import Counter, OrderedDict
from sklearn.feature_extraction.text import TfidfTransformer
import spacy
import sys
import os.path
import json
import pdb
from importlib import reload
reload(sys)
# sys.setdefaultencoding('utf8')


start_time = time.time()
if len(sys.argv) < 4:
    print("Wrong number of parameters!!! See correct format below:")
    print("Argument 1: Directory with stories")
    print("Argument 2: Output Directory")
    print("Argument 3: Directory with characters")
    exit(0)


# Get the fic ID and chapter ID
def get_para_chap_id(filename):
    ids = filename.split('/')[-1].split('_')
    return (ids[0], ids[1])


# Get characters from the Character coref files
def get_char_list(file, ficId, chapId, charFiles):
    # Read all files for characters
    # print(file.split('/')[-1].split('.')[0])
    # # exit(0)
    character_dict = {}
    char_file_name = char_path + file.split('/')[-1].split('.')[0] + ".chars"
    if len(charFiles) < 1:
        print("No character files found at" + char_path)
        exit(0)

    # Store the characters in a dict with normal names as keys
    if char_file_name in charFiles:
        chars = open(char_file_name).readlines()
        for char in chars:
            character_dict[char[char.find('_') + 1: char.find(')')].lower()] = char.strip('\n')
    else:
        # Print error if not character file found for fic
        print("No character file found for " + char_file_name)
    return character_dict


# Create co-occurance dictionary with adjectives occuring with entities
def adj_matrix(character_dict, data_list):
    chars = {}
    index = 0
    ngram = 8
    for row in data_list:
        if row['text'] in character_dict:
            if character_dict[row["text"]] in chars:
                for i in range(1, ngram + 1):
                    # print("                 ", index + i, len(data_list), data_list[index + i])
                    try:
                        if data_list[index + i]['pos'] == 'ADJ':
                            chars[character_dict[row["text"]]].update({data_list[index + i]['text']: 1})
                        if data_list[index - i]['pos'] == 'ADJ':
                            chars[character_dict[row["text"]]].update({data_list[index - i]['text']: 1})
                    except:
                        continue
            else:
                chars[character_dict[row["text"]]] = Counter()
                for i in range(1, ngram + 1):
                    try:
                        if data_list[index + i]['pos'] == 'ADJ':
                            chars[character_dict[row["text"]]].update({data_list[index + i]['text']: 1})
                        if data_list[index - i]['pos'] == 'ADJ':
                            chars[character_dict[row["text"]]].update({data_list[index - i]['text']: 1})
                    except:
                        continue
        index += 1
    return chars


# Create co-occurance dictionary with characters occuring with entities
def ship_matrix(character_dict, data_list):
    chars = {}
    index = 0
    ngram = 8
    for row in data_list:
        # print(row['text'])
        if row['text'] in character_dict:
            if character_dict[row["text"]] in chars:
                for i in range(1, ngram + 1):
                    try:
                        if data_list[index + i]['text'] in character_dict and data_list[index + i]['text'] != row['text']:
                            chars[character_dict[row["text"]]].update({character_dict[data_list[index + i]['text']]: 1})
                        if data_list[index - i]['text'] in character_dict and data_list[index - i]['text'] != row['text']:
                            chars[character_dict[row["text"]]].update({character_dict[data_list[index - i]['text']]: 1})
                    except:
                        continue

            else:
                chars[character_dict[row["text"]]] = Counter()
                for i in range(1, ngram + 1):
                    try:
                        if data_list[index + i]['text'] in character_dict and data_list[index + i]['text'] != row['text']:
                            chars[character_dict[row["text"]]].update({character_dict[data_list[index + i]['text']]: 1})
                        if data_list[index - i]['text'] in character_dict and data_list[index - i]['text'] != row['text']:
                            chars[character_dict[row["text"]]].update({character_dict[data_list[index - i]['text']]: 1})
                    except:
                        continue
        index += 1
    return chars


# Convert co-occurance dict to matrix with counts as values
def creating_cooccurence(adj_chars):
    adj_frame = pd.DataFrame.from_dict(adj_chars, orient='index').reset_index().fillna(0)
    col_names = list(adj_frame.columns.values)
    col_names = col_names[1:]
    char_list = []
    # Converting term counts to TFIDF
    for char in adj_frame["index"]:
        char_list.append(char)
    adj_frame = adj_frame.drop(["index"], axis=1)
    return adj_frame, char_list, col_names


# Rank matrix by TF-IDF
def TFIDF_construct(data_frame, char_list, col_names):
    tfidf = TfidfTransformer(norm='l2', use_idf=True, smooth_idf=True, sublinear_tf=False)
    mat = tfidf.fit_transform(data_frame).toarray()
    data_frame = pd.DataFrame(mat)
    data_frame.index = char_list
    data_frame.columns = col_names

    new_char_dict = {}
    char_dict = data_frame.to_dict('index')
    for key in char_dict:
        new_char_dict[key] = char_dict[key]
    return new_char_dict


# Sorting the dict by TF-IDF values
def create_ordered_dict(char_dict):
    sorted_dict = OrderedDict()
    for key in char_dict:
        counter_dict = OrderedDict()
        for key1, value in sorted(char_dict[key].items(), key=lambda k_v: k_v[1], reverse=True):
            if (value != 0):
                counter_dict[key1] = value
        sorted_dict[key] = counter_dict
    return sorted_dict


def write_to_json(output_path, sorted_dict):
    print("Writing JSON to " + output_path)
    with open(output_path, 'w') as fp:
        json.dump(sorted_dict, fp)


# Load spacy model for POS tagging
print("Loading NLP Model")
nlp = spacy.load('en_core_web_sm')
print("Model loaded")
path = sys.argv[1]
script_dir = os.path.dirname(__file__)  # <-- absolute dir the script is in

rel_path = os.path.join(sys.argv[1], '')
#path = os.path.join(script_dir, rel_path)

input_dir = sys.argv[1]
if not input_dir.startswith('/'):
    path = '../'+input_dir
print("Reading file from " + path)
allFiles = glob.glob(path + "/*.csv")

rel_path = sys.argv[3]
#char_path = os.path.join(script_dir, rel_path)
if not rel_path.startswith('/'):
    char_path = '../'+rel_path
else:
    char_path = rel_path
print("Reading characters from " + char_path)
charFiles = set(glob.glob(char_path + "/*"))

# Exit if no fic files found
if len(allFiles) < 1:
    print("No files found at" + path)
    exit(0)

# Iterate over fic files
for file in allFiles:
    print("Reading file: ", file)
    rel_path = os.path.join(sys.argv[2], '')
    adj_output_path = os.path.join(script_dir, rel_path) + file.split('/')[-1].split('.')[0] + "_adj_cooccurrence.json"
    if not adj_output_path.startswith('/'):
        adj_output_path = '../' + adj_output_path

    ship_output_path = os.path.join(script_dir, rel_path) + file.split('/')[-1].split('.')[0] + "_ship_cooccurrence.json"
    if not ship_output_path.startswith('/'):
        ship_output_path = '../' + ship_output_path

    text = []
    ficId, chapId = get_para_chap_id(file)
    df = pd.read_csv(file, index_col=None, header=0)
    for row in df.iloc[:, -1]:
        # text.append(unicode(row, errors='ignore'))
        text.append(str(row))

    character_dict = get_char_list(file, ficId, chapId, charFiles)

    # Write an empty file if no characters present in Coref char files
    if len(character_dict) < 1:
        write_to_json(adj_output_path, {})
        write_to_json(ship_output_path, {})
        continue

    # Remove $_ from characters as spacy model is unable to identify the POS tags
    text = ''.join(text)
    processed_text = []
    for word in text.split(" "):
        if word.startswith("($_"):
            processed_text.pop()
            word = word[word.find('_') + 1: word.find(')')]

        processed_text.append(word.lower())
    processed_text = ' '.join(processed_text)

    # Pass through the Spacy Model and create a list of dicts
    doc = nlp(str(processed_text))
    data_list = []
    count = 0
    for token in doc:
        if token.is_stop is False:
            data_list.append({'text': token.text, 'pos': token.pos_, 'tag': token.tag_, 'lemma': token.lemma_})
            count += 1

    df = pd.DataFrame(data_list)

    # Create adjective co-occurance dict
    adj_chars = adj_matrix(character_dict, data_list)
    # Create character co-occurance dict
    ship_chars = ship_matrix(character_dict, data_list)
    print("Creating co-occurence for ", file.split('/')[-1].split('.')[0])
    rel_path = os.path.join(sys.argv[2], '')
    adj_output_path = os.path.join(script_dir, rel_path) + file.split('/')[-1].split('.')[0] + "_adj_cooccurrence.json"
    if not adj_output_path.startswith('/'):
        adj_output_path = '../' + adj_output_path
    ship_output_path = os.path.join(script_dir, rel_path) + file.split('/')[-1].split('.')[0] + "_ship_cooccurrence.json"
    if not ship_output_path.startswith('/'):
        ship_output_path = '../' + ship_output_path

    # Create adjective and character co-occurance matrices and rerank by TF-IDF
    try:
        adj_frame, adj_char_list, adj_col_names = creating_cooccurence(adj_chars)
        adj_char_dict = TFIDF_construct(adj_frame, adj_char_list, adj_col_names)
        adj_sorted_dict = create_ordered_dict(adj_char_dict)
        write_to_json(adj_output_path, adj_sorted_dict)
    except:
        print("Error creating adjective co-occurence for file: ", file)
        write_to_json(adj_output_path, {})
    try:
        ship_frame, ship_char_list, shipcol_names = creating_cooccurence(ship_chars)
        ship_char_dict = TFIDF_construct(ship_frame, ship_char_list, shipcol_names)
        ship_sorted_dict = create_ordered_dict(ship_char_dict)
        write_to_json(ship_output_path, ship_sorted_dict)
    except:
        print("Error creating relationship co-occurence for file: ", file)
        write_to_json(ship_output_path, {})
