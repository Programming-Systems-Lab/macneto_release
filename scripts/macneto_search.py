from __future__ import print_function
import sys
import os
import argparse
import sqlite3
import math

import topics
from topics import Method
from scipy import spatial
from sklearn.externals import joblib
from decimal import *
import numpy as np
import time

import logging

getcontext().prec = 5
logging.basicConfig(filename='macneto.log',level=logging.INFO, format='%(threadName)s  %(levelname)s %(asctime)s %(message)s', datefmt='%m/%d/%Y %I:%M:%S %p')

class APK_Info:
    def __init__(self, apk_key):
        self.apk_key = apk_key
        self.map = dict()

    def add_mapping(self, ori, obfus):
        self.map[ori] = obfus

    def get_mapping(self, ori):
        return self.map[ori]

    def get_mappings(self):
        return self.map

def get_parser():
    parser = argparse.ArgumentParser()
    parser.add_argument('-o', '--ori', help = 'Original DB', type=str)
    parser.add_argument('-b', '--obfus', help = 'Obfuscat DB', type=str)
    parser.add_argument('-m', '--max', help = 'Max IDs', type=int)
    return parser

def cosine_sim(l1, l2):
    return 1 - spatial.distance.cosine(l1, l2)

def cosine_sim2(l1, l2):
    return np.dot(l1, l2)/(np.linalg.norm(l1) * np.linalg.norm(l2))

def compare(f1, f2):
    return abs(f1 - f2) <= 0.00001

def match(obj_map, mode):
    multi_ans = 0
    ranks = [0 for i in xrange(11)]

    start_time = time.time()
    for bm in obj_map:
        om = obj_map[bm]

        base_sim = 0.0
        if mode == 0:
            base_sim = cosine_sim2(bm.dist, om.dist)
        elif mode == 1:
            base_sim = cosine_sim2(bm.topic_dist, om.topic_dist)
        elif mode == 2:
            base_sim = cosine_sim2(bm.pca_dist, om.pca_dist)
        elif mode == 3:
            base_sim = cosine_sim2(bm.predict_dist, om.dist)
        else:
            print('Invalid mode: ' + str(mode))
            return

        #print('Ori: ' + str(om.dist))
        #print('Obf: ' + str(bm.dist))
        #print('Base Sim: ' + str(base_sim))
        #print()

        base_deci = Decimal(base_sim)
        higher = 0
        equal = 0
        #sim_record = list()
        for tmp in obj_map.values():
            if tmp == om:
                continue

            sim = 0.0
            if mode == 0:
                sim = cosine_sim2(bm.dist, tmp.dist)
            elif mode == 1:
                sim = cosine_sim2(bm.topic_dist, tmp.topic_dist)
            elif mode == 2:
                sim = cosine_sim2(bm.pca_dist, tmp.pca_dist)
            elif mode == 3:
                sim = cosine_sim2(bm.predict_dist, tmp.dist)
            else:
                print('Invalid mode: ' + str(mode))
                return

            if math.isnan(sim):
                continue

            #sim_record.append(sim)
            sim_deci = Decimal(sim)

            if sim_deci > base_deci:
                higher += 1
            elif sim_deci == base_deci:
                equal += 1

        if higher >= 10:
            ranks[10] += 1
        else:
            ranks[higher] += 1

        if equal >= 1:
            multi_ans += 1
        #print('Sims: ' + str(sim_record) + '\n')

    exec_time = time.time() - start_time
    print('Total search: ' + str(len(obj_map)))
    print('Ranks: ' + str(ranks))
    print('Multi ans: ' + str(multi_ans))
    print('Exec time: %s seconds: ' % exec_time)

def truncate(results):
    base_idx = 10
    base_sim = results[9][1]

    for i in xrange(10, len(results)):
        cur_sim = results[i]
        if cur_sim == base_sim:
            base_idx += 1
        else:
            break

    results = results[0: base_idx]
    return results

def mrr(ranks):
    mrr = 0.0
    check_len = min(10, len(ranks))
    total = sum(ranks)
    for i in xrange(check_len):
        mrr += float(ranks[i])/(i + 1)

    mrr /= total
    return mrr

def top_ranks(ranks):
    all = sum(ranks)
    top_1 = float(ranks[0])/all
    top_5 = float(sum(ranks[:5]))/all
    top_10 = float(sum(ranks[:10]))/all

    return top_1, top_5, top_10

def _search(searchable, apk, mode):
    search_results = list()
    for s in searchable:
        search_sim = 0.0
        if mode == 0:
            search_sim = cosine_sim2(apk.dist, s.dist)
        else:
            search_sim = cosine_sim2(apk.predict_dist, s.predict_dist)

        if math.isnan(search_sim):
            #print('Invalid obfus apk: %d' % obfus_apk.apk_id)
            continue

        search_sim = round(search_sim, 5)
        search_sim = Decimal(search_sim)

        search_tup = (s, search_sim)
        search_results.append(search_tup)
        search_results.sort(key = lambda o: o[1], reverse = True)
        if len(search_results) > 10:
            search_results = truncate(search_results)

    return search_results

def _search_best(searchable, apk, mode):
    search_best_sim = Decimal(0.0)
    best_apks = list()
    for s in searchable:
        sim = 0.0
        if mode == 0:
            sim = cosine_sim2(apk.dist, s.dist)
        else:
            sim = cosine_sim2(apk.predict_dist, s.predict_dist)

        if math.isnan(sim):
            #print('Invalid ori apk: %d' % ori_apk.apk_id)
            continue

        sim = round(sim, 5)
        sim = Decimal(sim)

        if sim > search_best_sim:
            search_best_sim = sim
            best_apks = list()
            best_apks.append(s)
        elif sim == search_best_sim:
            best_apks.append(s)

    return best_apks

def search(searchable, ori_tests, obfus_tests, mode):
    valid = 0
    search_stats = [0 for i in xrange(11)]
    start_time = time.time()
    for i in xrange(len(obfus_tests)):
        obfus_apk = obfus_tests[i]
        ori_apk = ori_tests[i]

        ori_best_apks = _search_best(searchable, ori_apk, mode)
        obfus_results = _search(searchable, obfus_apk, mode)

        #print('Original apk, closest search: %d %d %f' % (ori_apk.apk_id, ori_best_apks[0].apk_id, ori_best_sim))

        valid += 1
        best_pos = len(searchable)
        best_pos_sim = 0.0
        for obest in ori_best_apks:
            for i in xrange(len(obfus_results)):
                b_tup = obfus_results[i]
                if b_tup[0] == obest:
                    best_pos = min(best_pos, i)
                    best_pos_sim = b_tup[1]

        #print('Obfus apk, closest search: %d %d %f' % (obfus_apk.apk_id, obfus_results[0][0].apk_id, obfus_results[0][1]))
        #print('Best pos: %d %f' % (best_pos, best_pos_sim))

        if best_pos >= 10:
            search_stats[10] += 1
        else:
            search_stats[best_pos] += 1

    exec_time = time.time() - start_time
    logging.info('Total search queries: %d' % valid)
    logging.info('Ranks: ' + str(search_stats))
    logging.info('Top@1,5,10: ' + str(top_ranks(search_stats)))
    logging.info('MRR: ' + str(mrr(search_stats)))
    logging.info('Exec. time: %s secondes: ' % exec_time)

def read_maps(mappings):
    files = [f for f in os.listdir(mappings) if os.path.isfile(os.path.join(mappings, f)) and f.endswith('_m.csv')]
    print('Total mapping file: ' + str(len(files)))

    apk_infos = dict()
    for f in files:
        path = os.path.join(mappings, f)
        apk_key = f[:-6]
        apk_info = APK_Info(apk_key)
        apk_infos[apk_key] = apk_info
        with open(path, 'rb') as m_file:
            for line in m_file:
                info = line.split(',')
                apk_info.add_mapping(info[1].rstrip(), info[0])

        print('APK Key: ' + apk_key + ' mappings: ' +  str(len(apk_info.get_mappings())))

    return apk_infos

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

def set_reduced(objs, topics, pcas):
    for i in xrange(len(objs)):
        obj = objs[i]
        topic = topics[i]
        pca = pcas[i]

        obj.set_topic_dist(topic)
        obj.set_pca_dist(pca)

def set_predict(objs, predicts):
    for i in xrange(len(objs)):
        obj = objs[i]
        predict = predicts[i]
        obj.set_predict_dist(predict)

def read_apk_data(ori, obfus):
    ori_apks, ori_texts = topics.read_apk_data(ori)
    obfus_apks, obfus_texts = topics.read_apk_data(obfus)
    id_name, name_id, id_map = topics.read_id_name(ori, obfus)

    ori_topics = topics.predict(ori_texts)
    obfus_topics = topics.predict(obfus_texts)

    ori_pcas = topics.predict_pca(ori_apks)
    obfus_pcas = topics.predict_pca(obfus_apks)

    set_reduced(ori_apks, ori_topics, ori_pcas)
    set_reduced(obfus_apks, obfus_topics, obfus_pcas)

    deob = topics.predict_deep(obfus_apks)
    set_predict(obfus_apks, deob)

    obfus_flat = dict()
    for ba in obfus_apks:
        obfus_flat[ba.apk_id] = ba

    obj_map = dict()
    for oa in ori_apks:
        apk_id = oa.apk_id
        if apk_id in id_map:
            obfus_id = id_map[apk_id]
            ba = obfus_flat[obfus_id]
            obj_map[ba] = oa

    return obj_map

def read_test_apk_data(ori, obfus, test_ids):
    ori_apks, ori_texts = topics.read_apk_data(ori)
    searchable = list()
    ori_test = list()

    for o in ori_apks:
        if o.apk_id in test_ids:
            ori_test.append(o)
        else:
            searchable.append(o)

    id_name, name_id, id_map = topics.read_id_name(ori, obfus)

    obfus_apks, obfus_texts = topics.read_apk_data(obfus)
    obfus_flat = dict()
    for ba in obfus_apks:
        obfus_flat[ba.apk_id] = ba

    obfus_test = list()
    for oa in ori_test:
        apk_id = oa.apk_id
        obfus_id = id_map[apk_id]
        ba = obfus_flat[obfus_id]
        obfus_test.append(ba)

    print('Searchable apks: %d' % len(searchable))
    print('Test original apks: %d' % len(ori_test))
    print('Test obfus apks: %d' % len(obfus_test))

    return searchable, ori_test, obfus_test

def read_datas(ori, obfus, id_name, name_id, mappings):
    obfus_methods, obfus_texts = topics.read_data(obfus)
    #For experimental purpose
    #set_topics(obfus_methods, obfus_texts)

    ori_methods = topics.read_data(ori, True)[0]
    ori_methods = [x for x in ori_methods if len(x.getSeq()) >= 5]

    print('Total original methods: ' + str(len(ori_methods)))
    print('Total obfus methods: ' + str(len(obfus_methods)))
    print('Total mappings: ' + str(len(mappings)))

    obfus_flatten = dict()
    for bm in obfus_methods:
        obfus_flatten[bm.getKey()] = bm

    valid = 0
    invalid_set = set()
    seq_same = 0

    obj_map = dict()
    for om in ori_methods:
        #Find counter parts
        apk_id = om.getApkId()

        if apk_id not in id_name:
            #print('Check apk: ' + str(apk_id) + ' ' + str(om.getId()) + ' ' + str(om.getKey()))
            invalid_set.add(apk_id)
            continue

        apk_name = id_name[apk_id]
        m_mappings = mappings[apk_name]

        m_name = om.getKey()
        if m_name in m_mappings.get_mappings():
            obfus_name = m_mappings.get_mappings()[m_name]
            if obfus_name in obfus_flatten:
                bm = obfus_flatten[obfus_name]
                valid += 1
                if om.getSeq() == bm.getSeq():
                    seq_same += 1
                else:
                    print('Ori seq: ' + str(om.getSeq()))
                    print('Obf seq: ' + str(bm.getSeq()))
                    print()

                obj_map[bm] = om

    print('Valid mappings: ' + str(valid))
    print('Seq same: ' + str(seq_same))
    print('Invalid set: ' + str(len(invalid_set)))

    return obj_map

def read_test_ids(set_id):
    test_ids = joblib.load('model/test_ids_' + str(set_id) + '.p')
    print('Total test apks: %d' % len(test_ids))
    return test_ids

def predict_and_set(searchable, ori_test, obfus_test, model_path, mode):
    search_proj = None
    ori_proj = None
    deob = None

    if mode == 1:
        search_proj = topics.predict_pca(searchable, model_path)
        ori_proj = topics.predict_pca(ori_test, model_path)
        deob = topics.predict_pca(obfus_test, model_path)
    else:
        search_proj = topics.predict_deep(searchable, model_path)
        ori_proj = topics.predict_deep(ori_test, model_path)
        deob = topics.predict_deep(obfus_test, model_path)

    set_predict(searchable, search_proj)
    set_predict(ori_test, ori_proj)
    set_predict(obfus_test, deob)

def main():
    parser = get_parser()
    args = parser.parse_args()
    ori = args.ori
    obfus = args.obfus
    max_ids = args.max
    #mappings = args.map

    for i in xrange(max_ids + 1):
        logging.info('Experiment %d' % (i))
        test_ids = read_test_ids(i)
        searchable, ori_test, obfus_test = read_test_apk_data(ori, obfus, test_ids)

        logging.info('Naive search')
        search(searchable, ori_test, obfus_test, 0)

        pca_all_path = 'model/apk_pca_' + str(i) + '.p'
        predict_and_set(searchable, ori_test, obfus_test, pca_all_path, 1)

        logging.info('PCA search')
        search(searchable, ori_test, obfus_test, 1)

        pca_path = 'model/deep_apk_pca_' + str(i) + '.h5'
        predict_and_set(searchable, ori_test, obfus_test, pca_path, 2)

        logging.info('Deep PCA search')
        search(searchable, ori_test, obfus_test, 2)

        #topic_path = 'model/deep_apk_topic_' + str(i) + '.h5'
        #predict_and_set(searchable, ori_test, obfus_test, topic_path, 3)

        #logging.info('Deep topic search')
        #search(searchable, ori_test, obfus_test, 3)

        logging.info('End experiment %d' % (i))

    #apk_infos = read_maps(mappings)
    #id_name, name_id, id_map = read_id_name(ori, obfus)
    #obj_map = read_datas(ori, obfus, id_name, name_id, apk_infos)
    #obj_map = read_apk_data(ori, obfus)
    #print('Match by instructions')
    #match(obj_map, 0)

    #print('Match by topics')
    #match(obj_map, 1)

    #print('Match by pca')
    #match(obj_map, 2)

    #print('Match by deep learn')
    #match(obj_map, 3)

if __name__ == '__main__':
    main()
