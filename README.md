# Macneto: Obfuscation Resilient Search through Executable Classification

## Data collection
We collect android APKs from [fdroid](https://f-droid.org/en/) 

## Concepts
Macneto contains two major modules: program (executable) analysis and machine learning, including PCA and deep learning.
To demonstrate the capability of Macneto, we attempt to search for programs, mapping a program obfuscated by an obfucator back to its original version.
To deobfuscate programs, Macneto needs to learn the difference between original binary and its obfuscated version.
Thus, we need to program-analyze both versions of binary.
From step 1 to step 6, you will need to run them on both of your original apk and obfuscated apk (or jars).

To facilitate the analysis, you can create a directory for analyzing your original binaries and create another directory for analyzing your obfuscated binaries.
For learning the mapping between an executable and its obfuscated counterpart, we use [sklearn](http://scikit-learn.org/stable/), [tensorflow](https://www.tensorflow.org/) and [keras](https://keras.io/).
For installing these frameworks, please check out the details on their websites.

## Steps

### Step 1: Create database </br>
`java -cp macneto-inst-analysis-0.0.1-SNAPSHOT.jar edu.columbia.cs.psl.macneto.db.DocManager`

This command will create a database `db/macneto.db`. You will have to create both databases for your original binaries and your obfuscated binaries. This means that you will have <b>n + 1</b> databases, where n is the number of your obfuscators.

### Step 2: Generate call graphs </br>
`python macneto_container_v2/graph_gen.py -a YOUR_APKBASE -g callgraphs -e macneto_container_v2/macneto-inst-analysis-0.0.1-SNAPSHOT.jar -f android/platforms -t 16 -ext jar`

For details of these options, please refer to `graph_gen.py`. Again, this command needs to run on both original apks and obfuscated apks/jars. `-a` can specify the directory of your apks. `-g` can specify where you want to store your callgraphs. We usually keep it as `./callgraphs`. `-t` can specify the thread number. `-ext` can specify which types of binary you want to analyze.

### Step 3: Compute instruction distribution </br>
`java -cp macneto-inst-analysis-0.0.1-SNAPSHOT.jar edu.columbia.cs.psl.macneto.inst.MachineCodeDriver -c your/apk/location`

This step computes the instruction distribution of each method in all of your apks. Again, this step needs to run on both original and obfuscated apks. 

### Step 4: Learning phase
`python macneto_learn.py -d ORIGINAL/db/macneto.db -b OBFUSCATED/db/macneto.db -t 32 -i 10000 -a deep`

`-t` specifies how many principal components you want; `-i` specifies how many iterations you want; you can leave `-a` as it is.
This step first compute PCA from the original executables, use it label the obfuscated counterparts and then learn the mapping through ANN.

### Step 5: Search phase
`python macneto_search.py -o ORIGINAL/db/macneto.db -b OBFUSCATED/db/macneto.db -m 7`

`-m` specifies the experiment (fold) number (m + 1). 
In our paper, we conduct k-fold analysis, where `k = 8`
