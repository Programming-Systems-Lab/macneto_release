import argparse
import os
import sys
import trace
import logging
import subprocess
from time import time
from Queue import Queue
from threading import Thread

thread_num = 1
graph_repo = None
android_framework = None
executor = None

logging.basicConfig(filename='graph_gen.log')
log_hdlr = logging.StreamHandler()
log_hdlr.setFormatter(logging.Formatter(logging.BASIC_FORMAT))
logger = logging.getLogger('graph_gen')
logger.setLevel(logging.DEBUG)
logger.addHandler(log_hdlr)

class GraphWorker(Thread):
    def __init__(self, queue, threadID, isApk):
        Thread.__init__(self)
        self.queue = queue
        self.threadID = threadID
        self.isApk = isApk

    def run(self):
        while True:
            apk_path = self.queue.get()
            try:
                command = list()
                command.append('java')
                command.append('-Xmx2g')
                command.append('-cp')
                command.append(executor)
                command.append("edu.columbia.cs.psl.macneto.callgraph.AndroidAnalyzer")
                command.append('-i')
                command.append(apk_path)
                command.append('-o')
                command.append(graph_repo)
                command.append('-a')
                command.append(android_framework)
                if self.isApk == True:
                    command.append('-apk')
                
                subprocess.check_call(command, stdout=open('output.txt', 'a+'), stderr=subprocess.STDOUT)
                
                logger.info('Thread ' + str(self.threadID) + ' completes ' + apk_path)

            except Exception, e:
                #traceback.print_exc(file=sys.stdout)
                logger.error('Fail ' + str(command))
                logger.exception(e)

            self.queue.task_done()

def collect_data(data, extension):
    data_list = list()
    ext_len = len(extension)
    for dir, subdir, filelist in os.walk(data):
        for fname in filelist:
            if fname.endswith(extension):
                apk_name = fname[0:-ext_len]
                single_data = os.path.join(dir, fname)
                data_list.append(single_data)

    return data_list

def gen_graphs(apks, isApk):
    worker_queue = Queue()
    counter = 0
    for x in xrange(thread_num):
        worker = GraphWorker(worker_queue, counter, isApk)
        worker.daemon = True
        counter = counter + 1
        worker.start()

    ts = time()
    for apk_path in apks:
        worker_queue.put(apk_path)

    worker_queue.join()
    complete_time = time()
    logger.info('Total gen graph time: ' + str(complete_time - ts))

    succ = 0
    for apk_path in apks:
        ridx = apk_path.rfind('/')
        apk_name = apk_path
        if ridx != -1:
            apk_name = apk_path[ridx + 1: -4]
        graph_path = graph_repo + '/' + apk_name + '.json'
        if os.path.exists(graph_path):
            succ += 1

    logger.info('Total graphs: ' + str(len(apks)))
    logger.info('Successful graphs: ' + str(succ))

def main():
    global thread_num
    global graph_repo
    global android_framework
    global executor

    parser = argparse.ArgumentParser()
    parser.add_argument('-a', '--apks', type = str, required = True)
    parser.add_argument('-g', '--graphs', type = str, required = True)
    parser.add_argument('-e', '--executor', type = str, required = True)
    parser.add_argument('-f', '--framework', type = str, required = True)
    parser.add_argument('-t', '--threads', type=int, required = False)
    parser.add_argument('-ext', '--extension', type = str, required = True)
    args = parser.parse_args()

    apks_path = args.apks
    if not os.path.exists(apks_path):
        print 'Invalid apk directory: ' + apks_path
        sys.exit(-1)
    print 'Confirm apks path: ' + apks_path
    
    executor = args.executor
    if not os.path.exists(executor):
        print 'Invalid executor: ' + executor
        sys.exit(-1)
    print 'Confirm executor path: ' + executor

    android_framework = args.framework
    if not os.path.exists(android_framework):
        print 'Invalid framework: ' + android_framework
        sys.exit(-1)
    print 'Confirm framework: ' + android_framework

    graph_repo = args.graphs
    if not os.path.exists(graph_repo):
        print 'Create graph repo: ' + graph_repo
        os.mkdir(graph_repo)
    print 'Confirm graph_path: ' + graph_repo

    if args.threads is not None:
        thread_num = args.threads
    print 'Confirm thread number: ' + str(thread_num)
    
    ext = args.extension
    print 'Confirm extension: ' + ext

    apks = collect_data(apks_path, ext)
    logger.info('Total apks: ' + str(len(apks)))
    if ext == 'jar':
        gen_graphs(apks, False)
    else:
        gen_graphs(apks, True)

if __name__ == '__main__':
    main()
