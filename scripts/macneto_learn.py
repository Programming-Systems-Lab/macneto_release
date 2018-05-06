from __future__ import print_function
from time import time

from sklearn.feature_extraction.text import TfidfVectorizer, CountVectorizer
from sklearn.decomposition import NMF, LatentDirichletAllocation, PCA
from sklearn.datasets import fetch_20newsgroups
from sklearn.externals import joblib

from keras.models import Sequential
from keras.layers import Dense
from keras.models import load_model

import numpy as np

import argparse
import os
import sys

import sqlite3
import json
import random

import logging
from threading import Thread

#n_samples = 2000
n_features = 500
#n_components = 10
n_top_words = 20
iter = 1000

logging.basicConfig(filename='macneto.log',level=logging.INFO, format='%(threadName)s  %(levelname)s %(asctime)s %(message)s', datefmt='%m/%d/%Y %I:%M:%S %p')

class DeepWorker:
    def __init__(self, id, train_ori, train_obfus, test_ori, text_dict):
        #Thread.__init__(self)
        self.id = id
        self.train_ori = train_ori
        self.train_obfus = train_obfus
        self.test_ori = test_ori
        self.text_dict = text_dict

    def run(self):
        logging.info('Worker %d is running' % (self.id))
        test_ids = list()
        for t in self.test_ori:
            test_ids.append(t.apk_id)

        joblib.dump(test_ids, 'model/test_ids_' + str(self.id) + '.p')
        logging.info('Worker %d dumps test ids' % (self.id))

        pca_only_path = 'model/apk_pca_' + str(self.id) + '.p'
        pca_all(self.train_ori, self.train_obfus, 32, pca_only_path)
        logging.info('Worker %d completes pca only' % (self.id))

        pca_path = 'model/deep_apk_pca_' + str(self.id) + '.h5'
        #print('pca path: ' + pca_path)
        ann_pca(self.train_ori, self.train_obfus, iter, pca_path)
        logging.info('Worker %d completes deep pca' % (self.id))

        #topic_path = 'model/deep_apk_topic_' + str(self.id) + '.h5'
        #print('topic path: ' + topic_path)
        #ann_topic(self.train_ori, self.train_obfus, self.text_dict, iter, topic_path)
        #logging.info('Worker % d completes deep topics' % (self.id))

class APK:
    def __init__(self, apk_id):
        self.apk_id = apk_id

    def set_dist(self, dist):
        self.dist = dist

    def set_predict_dist(self, predict_dist):
        self.predict_dist = predict_dist

    def set_topic_dist(self, topic_dist):
        self.topic_dist = topic_dist

    def set_pca_dist(self, pca_dist):
        self.pca_dist = pca_dist

    def __eq__(self, other):
        if self.apk_id == other.apk_id:
            return True;
        else:
            return False

    def __hash__(self):
        return self.apk_id

class Method:
    def __init__(self, apk_id, m_id, key, seq, dist):
        self.apk_id = apk_id
        self.m_id = m_id
        self.key = key
        self.seq = seq
        self.dist = dist

    def getApkId(self):
        return self.apk_id

    def getId(self):
        return self.m_id

    def getKey(self):
        return self.key

    def getSeq(self):
        return self.seq

    def getDist(self):
        return self.dist

    def setTopicDist(self, topic_dist):
        self.topic_dist = topic_dist

    def getTopicDist(self):
        return self.topic_dist

    def __eq__(self, other):
        if self.apk_id == other.apk_id and self.m_id == other.m_id:
            return True
        else:
            return False

    def __hash__(self):
        u_key = str(self.apk_id) + '-' + str(self.m_id)
        return hash(u_key)

def get_parser():
    parser = argparse.ArgumentParser()
    parser.add_argument('-t', '--topic', help = 'Topic number', type=str)
    parser.add_argument('-i', '--iter', help = 'Iteration', type=str)
    parser.add_argument('-d', '--db', help = 'Database', type=str)
    parser.add_argument('-b', '--ob', help = 'Obfuscated db', type=str)
    parser.add_argument('-a', '--alg', help= 'Use which algorithm', type=str)
    return parser

def gen_dist(freq_map):
    dist = [0 for i in xrange(252)]

    for inst in freq_map:
        dist[int(inst)] = freq_map[inst]

    #dist = [round(x/total, 5) for x in dist]
    return dist

def read_id_name(ori, obufs):
    #name_id for obufs apks
    #id_name for original apks

    obfus_conn = sqlite3.connect(obufs)
    obfus_cursor = obfus_conn.cursor()
    obfus_rows = obfus_cursor.execute('SELECT * FROM APP')
    name_id = dict()
    for ob in obfus_rows:
        apk_name = ob[1]
        name_id[apk_name] = ob[0]

    ori_conn = sqlite3.connect(ori)
    ori_cursor = ori_conn.cursor()
    ori_rows = ori_cursor.execute('SELECT * FROM APP')

    id_name = dict()
    id_map = dict()
    for o in ori_rows:
        apk_name = o[1]
        obfus_key = apk_name + '_obfus'
        if obfus_key in name_id:
            id_name[o[0]] = o[1]
            id_map[o[0]] = name_id[obfus_key]

    print('Total available apks in db: ' + str(len(id_name)))
    return id_name, name_id, id_map

def read_apk_data(db):
    conn = sqlite3.connect(db)
    cursor = conn.cursor()
    rows = cursor.execute('SELECT * FROM METHOD')

    apk_freqs = dict()
    for row in rows:
        apk_id = row[1]

        if apk_id not in apk_freqs:
            apk_freqs[apk_id] = dict()

        apk_dict = apk_freqs[apk_id]
        freq_dict = json.loads(row[5])

        for opcode in freq_dict:
            if opcode not in apk_dict:
                apk_dict[opcode] = freq_dict[opcode]
            else:
                apk_dict[opcode] += freq_dict[opcode]

    texts = list()
    apks = list()
    for apk_id in apk_freqs:
        apk = APK(apk_id)
        apks.append(apk)

        apk_dict = apk_freqs[apk_id]
        dist = gen_dist(apk_dict)
        apk.set_dist(dist)

        op_list = list()

        for op in apk_dict:
            freq = apk_dict[op]
            for i in xrange(int(freq)):
                op_list.append(str(op))

        op_string = unicode(' '.join(op_list))
        #print('apk ' + str(apk_id) + ' ops: ' + str(len(op_list)))
        #print('inst dist ' + str(apk_dict))
        texts.append(op_string)

    return apks, texts

def read_data(db, load_topic = False):
    print("Connect to database: " + db)
    conn = sqlite3.connect(db)
    cursor = conn.cursor()
    rows = cursor.execute('SELECT * FROM METHOD')

    methods = list()
    texts = list()

    max_len = -1
    total_len = 0
    for row in rows:
        m_id = row[0]
        apk_id = row[1]
        key = row[4]

        freq_dict = json.loads(row[5])
        dist = gen_dist(freq_dict)
        op_list = list()
        for op in freq_dict:
            freq = freq_dict[op]
            for i in xrange(int(freq)):
                op_list.append(op)
        op_string = ' '.join(op_list)

        seq = json.loads(row[6])
        #print(str(id) + ' ' + str(apk_id) + ' ' + key + str(op_string) + ' ' + str(seq))
        max_len = max(max_len, len(seq))
        total_len = total_len + len(seq)

        method = Method(apk_id, m_id, key, seq, dist)
        methods.append(method)
        texts.append(op_string)

        if load_topic:
            topic_dist = json.loads(row[3])
            method.setTopicDist(topic_dist)

    avg_len = float(total_len)/len(methods)
    print("Total methods: " + str(len(methods)))
    print("Max len: " + str(max_len))
    print("Avg. len: " + str(avg_len))

    return methods, texts

def update_data(methods, doc_dist, db):
    conn = sqlite3.connect(db)
    cursor = conn.cursor()

    for i in xrange(len(methods)):
        m = methods[i]
        dist = doc_dist[i].round(decimals=5).tolist()
        dist_str = json.dumps(dist)
        #print(str(m.getId()) + dist
        cursor.execute('UPDATE METHOD SET TOPIC_DIST = ? WHERE ID = ?', (dist_str, m.getId()))

    conn.commit()
    print('Complete update topic dist')

def print_top_words(model, feature_names, n_top_words):
    for topic_idx, topic in enumerate(model.components_):
        message = "Topic #%d: " % topic_idx
        message += " ".join([feature_names[i]
                             for i in topic.argsort()[:-n_top_words - 1:-1]])
        print(message)
    print()

def gen_matrix(apks):
    dists = list()
    for apk in apks:
        dists.append(apk.dist)

    return np.array(dists)

def split(ori_apks, obfus_apks, test_num):
    indices = random.sample(xrange(len(ori_apks)), test_num)
    indices = set(indices)

    train_ori = list()
    test_ori = list()
    train_obfus = list()
    test_obfus = list()

    test_ids = list()
    for i in xrange(len(ori_apks)):
        if i in indices:
            test_ori.append(ori_apks[i])
            test_obfus.append(obfus_apks[i])

            test_ids.append(ori_apks[i].apk_id)
        else:
            train_ori.append(ori_apks[i])
            train_obfus.append(obfus_apks[i])

    print('Training set: %d %d' % (len(train_ori), len(train_obfus)))
    print('Testing set: %d %d' % (len(test_ori), len(test_obfus)))

    joblib.dump(test_ids, 'model/test_ids.p')
    return train_ori, test_ori, train_obfus, test_obfus

def pair_apks(ori_apks, obfus_apks, id_map):
    obfus_flat = dict()
    for ba in obfus_apks:
        obfus_flat[ba.apk_id] = ba

    ori_ret = list()
    obfus_ret = list()
    for oa in ori_apks:
        apk_id = oa.apk_id
        if apk_id in id_map:
            obfus_id = id_map[apk_id]
            ba = obfus_flat[obfus_id]
            obfus_ret.append(ba)
            ori_ret.append(oa)

    return ori_ret, obfus_ret

def encode(ori_apks, obfus_apks, iter):
    ori_dist = gen_matrix(ori_apks)
    obfus_dist = gen_matrix(obfus_apks)

    start_tim = time()
    encoded = Dense(128, activation = 'relu')(ori_dist)
    decoded = Dense(256)

def ann(ori_apks, obfus_apks, iter):
    ori_dist = gen_matrix(ori_apks)
    obfus_dist = gen_matrix(obfus_apks)

    start_time = time()
    model = Sequential()
    model.add(Dense(350, input_dim=252, activation='relu'))
    model.add(Dense(300, activation='relu'))
    model.add(Dense(252, activation='relu'))
    model.compile(loss='cosine_proximity', optimizer='adam', metrics=['cosine', 'top_k_categorical_accuracy'])

    model.fit(obfus_dist, ori_dist, epochs=iter, batch_size=10)
    exec_time = time() - start_time

    scores = model.evaluate(obfus_dist, ori_dist)
    print("\n%s: %.2f" % (model.metrics_names[1], scores[1]))
    model.save('model/deep_apk.h5')
    print('Train time: %s seconds' % exec_time)

def ann_pca(ori_apks, obfus_apks, iter, path):
    ori_dist = gen_matrix(ori_apks)
    obfus_dist = gen_matrix(obfus_apks)

    start_time = time()
    pca_dist = proj_pca(ori_dist, 32)

    total_x = np.concatenate((ori_dist, obfus_dist), axis = 0)
    total_y = np.concatenate((pca_dist, pca_dist), axis = 0)
    score_msg = _ann(total_x, total_y, iter, path)
    logging.info('Deep pca score: ' + score_msg)

    end_time = time()
    exec_time = end_time - start_time
    logging.info('Deep PCA time: %s sec' % exec_time)

def ann_topic(ori_apks, obfus_apks, text_dict, iter, path):
    ori_texts = list()
    for apk in ori_apks:
        if apk not in text_dict:
            logging.error('Suspicious apk: ' + str(apk.apk_id))
            sys.exit(-1)
        text = text_dict[apk]
        ori_texts.append(text)

    ori_dist = gen_matrix(ori_apks)
    obfus_dist = gen_matrix(obfus_apks)

    start_time = time()
    topic_dist = proj_lda(ori_texts, 32, 500)

    total_x = np.concatenate((ori_dist, obfus_dist), axis = 0)
    total_y = np.concatenate((topic_dist, topic_dist), axis = 0)
    score_msg = _ann(total_x, total_y, iter, path)
    logging.info('Deep topic score: ' + score_msg)

    end_time = time()
    exec_time = end_time - start_time
    logging.info('Deep Topic time: %s sec' % (exec_time))

def _ann(total_x, total_y, iter, path):
    #start_time = time()
    model = Sequential()
    model.add(Dense(128, input_dim=252, activation='relu'))
    model.add(Dense(64, activation = 'relu'))
    model.add(Dense(32, activation = 'relu'))

    model.compile(loss='cosine_proximity', optimizer='adam', metrics=['cosine', 'top_k_categorical_accuracy'])

    model.fit(total_x, total_y, epochs=iter, batch_size=10)
    #exec_time = time() - start_time

    scores = model.evaluate(total_x, total_y)
    #logging.info("\n%s: %.2f" % (model.metrics_names[1], scores[1]))

    #model.save('model/deep_apk.h5')
    model.save(path)
    #logging.info('Train time: %s seconds' % exec_time)
    score_msg = model.metrics_names[1] + str(scores[1])
    return score_msg

def predict_deep(apks, path):
    model = load_model(path)
    data = gen_matrix(apks)
    deep_dist = model.predict(data)
    ret = list()
    for d in deep_dist:
        ret.append(d.round(decimals=5).tolist())

    return ret

def proj_pca(dist, comp):
    pca = PCA(n_components=comp)
    pca.fit(dist)
    pca_dist = pca.transform(dist)
    return pca_dist
    #ret = list()
    #for d in pca_dist:
    #    ret.append(d.round(decimals=5).tolist())

    #return ret

def pca_all(ori_train, obfus_train, comp, path):
    ori_dists = gen_matrix(ori_train)
    obfus_dists = gen_matrix(ori_train)

    start_time = time()
    total = np.concatenate((ori_dists, obfus_dists), axis = 0)
    pca = PCA(n_components=comp)
    pca.fit(total)
    end_time = time()
    exec_time = end_time - start_time
    logging.info('PCA Alltime: %s sec' % (exec_time))

    joblib.dump(pca, path)
    return pca

def pca(apks, comp):
    dists = gen_matrix(apks)

    logging.info("Computing pca, n_samples=%d" % len(apks))
    pca = PCA(n_components=comp)
    t0 = time()
    pca.fit(dists)
    logging.info("done in %0.3fs." % (time() - t0))

    joblib.dump(pca, 'model/pca_apk.p')

def predict_pca(apks, model_path):
    pca = joblib.load(model_path)
    data = gen_matrix(apks)

    pca_dist = pca.transform(data)
    return pca_dist
    #ret = list()
    #for d in pca_dist:
    #    ret.append(d.round(decimals=5).tolist())

    #return ret

def proj_lda(texts, topic, iteration):
    #print('Extracting tf features for LDA')
    tf_vectorizer = CountVectorizer(max_df=1.0, min_df=2, max_features=n_features)
    tf = tf_vectorizer.fit_transform(texts)

    #print("Fitting LDA models with tf features, n_samples=%d and n_features=%d..." % (len(texts), n_features))
    lda = LatentDirichletAllocation(n_components=topic, max_iter=iteration, learning_method='online', learning_offset=50., random_state=0)
    t0 = time()
    lda.fit(tf)
    #print("done in %0.3fs." % (time() - t0))

    doc_dist = lda.transform(tf)
    return doc_dist

def lda(data, topic, iteration):
    print("Extracting tf features for LDA...")

    tf_vectorizer = CountVectorizer(max_df=1.0, min_df=2, max_features=n_features, stop_words='english')
    tf = tf_vectorizer.fit_transform(data)

    print("Fitting LDA models with tf features, n_samples=%d and n_features=%d..." % (len(data), n_features))
    lda = LatentDirichletAllocation(n_components=topic, max_iter=iteration, learning_method='online', learning_offset=50., random_state=0)
    t0 = time()
    lda.fit(tf)
    print("done in %0.3fs." % (time() - t0))

    print("\nTopics in LDA model:")
    tf_feature_names = tf_vectorizer.get_feature_names()
    print_top_words(lda, tf_feature_names, n_top_words)

    doc_dist = lda.transform(tf)
    #for n in xrange(0, 10):
    for n in range(doc_dist.shape[0]):
        print(str(n) + "th doc: " + str(doc_dist[n]))

    #print('Check voc: ' + str(tf_vectorizer.vocabulary_))
    joblib.dump(lda, 'model/lda_apk.p')
    joblib.dump(tf_vectorizer, 'model/tf_apk.p')
    return doc_dist

def predict(data):
    lda = joblib.load('model/lda_apk.p')
    tf_vectorizer = joblib.load('model/tf_apk.p')
    #print('Check predict voc: ' + str(tf_vectorizer.vocabulary_))

    tf = tf_vectorizer.transform(data)
    doc_dist = lda.transform(tf)
    ret = list()
    for d in doc_dist:
        ret.append(d.round(decimals=5).tolist())

    return ret

def main():
    global iter
    parser = get_parser()
    args = parser.parse_args()

    db = args.db
    topic = int(args.topic)
    iter = int(args.iter)
    alg = args.alg

    #methods, texts = read_data(db)
    #doc_dist = lda(texts, topic, iter)
    #update_data(methods, doc_dist, db)
    apks, apk_texts = read_apk_data(db)
    if alg == 'pca':
        pca(apks, topic)
    elif alg == 'deep':
        ob_db = args.ob
        ob_apks, ob_apk_texts = read_apk_data(ob_db)
        id_name, name_id, id_map = read_id_name(db, ob_db)
        random.shuffle(apks)

        ori_apks, obfus_apks = pair_apks(apks, ob_apks, id_map)
        #train_ori, test_ori, train_obfus, test_obfus = split(ori_apks, obfus_apks, 200)

        k = -1
        if len(ori_apks) % 200 == 0:
            k = len(ori_apks)/200
        else:
            k = len(ori_apks)/200 + 1

        text_dict = dict()
        for j in xrange(len(apks)):
            apk = apks[j]
            text = apk_texts[j]
            text_dict[apk] = text

        for i in xrange(k):
            start = i * 200
            end = min(start + 200, len(ori_apks))

            test_ori = ori_apks[start:end]
            test_obfus = obfus_apks[start:end]
            logging.info('Worker %d test lens: %d %d' % (i, len(test_ori), len(test_obfus)))

            train_ori = ori_apks[:start] + ori_apks[end:]
            train_obfus = obfus_apks[:start] + obfus_apks[end:]
            logging.info('Worker %d train lens: %d %d' % (i, len(train_ori), len(train_obfus)))

            worker = DeepWorker(i, train_ori, train_obfus, test_ori, text_dict)
            worker.run()
            #threads.append(worker)

        #for w in threads:
        #    w.start()

        #for w in threads:
        #    w.join()

    else:
        doc_sit = lda(apk_texts, topic, iter)

if __name__ == '__main__':
    main()
