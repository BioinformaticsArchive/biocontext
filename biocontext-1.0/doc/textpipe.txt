TextPipe, v. 1.0

This is a software package for wrapping of text-mining (or general data processing-) components, allowing simple application and connection of the wrapped components.

In order to wrap a component, create a Java class for your component, and extend the uk.ac.man.pubtools.Annotator class. An example component is available: uk.ac.man.pubtools.annotators.examples.HelloWorldAnnotator.

The annotators have two functions that have to be implemented:
	getOutputFields() needs to return an array containing the names of the output columns that your application returns (in the case of an NER application, this could for example be {"ID", "start", "end"}, for the entity id and offsets. 
	
	process(Map<String,String>) needs to return a list of Map<String,String> objects. The input to the function is a Map with key-value pairs, providing the required information for the application to process the request. When processing documents, the text of a document can be found under the doc_text key, and the document id can be found under the doc_id key. For example, an NER application would read the doc_text variable, perform any operations (e.g. dictionary matching or application of machine-learning methods) on the text. As mentioned, the output should be a list of Map<String,String> objects - in the case of the NER tool, each Map would hold an entity, and the list would hold all entities.
	
	There are a number of additional methods that can be implemented - see the Annotator class for details.
	
Once you have created an Annotator, there are a number of things you can do with it.

You can apply it to text documents, storing the output in a database table:

$ java -jar pubtools.jar [DOCUMENT INPUT OPTIONS] --annotator <annotator class name> [--params ...] --compute <database> <table> --dbHost-<database> <host> --dbUsername-<database> <user> --dbPassword-<database> <password> --dbSchema-<database> <schema> [--dbPort-<database> <port>] [--clearCompute] [--check] [--insertDummyRecords] [--batch <n>] [--report <n>]

	(document input options are given below)

	--annotator <annotator class name>: specifies which annotator should be loaded
	--params k1=v1;k2=v2 : provides initialization data to the annotator (typically for configuration); only used if the init() function is implemented.
	
	--compute <database> <table>: tells TextPipe to perform computations on the documents, and store the results in the database <database> under table <table>. 
	
	--db... options: login details for the database
	
	--clearCompute: instructs TextPipe to remove the table if it already exists, and then create it again (useful if the table doesn't exist already)
	
	--check: check if results for a particular document already exist, avoiding re-computations of documents (results will be overwritten if this flag is not given)
	
	--insertDummyRecords: will insert a row full of NULL values (except for the document id column) for documents with no results. This ensures that a record is made of a document being processed, even if it does not result in any entries.
	
	--batch <n>: process documents in batches of <n> documents (does not have effect if the relevant methods are not implemented by the annotator)
	
	--report <n>: report progress every <n> documents.
	
	DOCUMENT INPUT OPTIONS:
	Documents can be read in a number of different formats, as specified by the LINNAEUS document-parsing library. Options are given below:

		--text <text file(s)>
		 Will read a set of text files

		--textDir <path> [--recursive]
		 Will read a set of files ending with .txt from the specified directory. If --recursive is given, subdirectories will 
		 also be searched.

		--pmcIndex <pmc index file> --pmcBaseDir <path> --dtd <dtds>
		 Will read a set of PMC documents specified by the given master file, located in the given base directory (the master 
		 file provides lower paths). The paths to .dtd files required for parsing also need to be specified. See formats.txt for format spec.

		--pmcDir <pmc directory> --dtd <dtds> [--recursive]
		 Will read all PMC documents in the given directory (and if recursive is specified, subdirectories).

		--medlineIndex <medline index file> --medlineBaseDir <path>
		 Will read a set of MEDLINE documents specified by the given master file, located in the given base directory (the 
		 master file provides lower paths). See formats.txt for format spec.

		--eutils <query>
			Performs an NCBI E-utils query and returns all documents that match the particular query. 

Example (replace database login settings as appropriate): 

java -jar pubtools.jar --eutils 'Gerner LINNAEUS' --annotator uk.ac.man.pubtools.annotators.examples.HelloWorldAnnotator --compute db tmp --dbHost-db localhost --dbUsername-db user --dbPassword-db pass --dbSchema-db schema --clearCompute --check --insertDummyRecords --report 1

You can run the annotator as a service, in two different modes:

HTTP server (for making services available to the world):

java -jar textpipe.jar --annotator <annotator class name> --server [--port <port>] [--conns <n>] [--params ...] [--cache <database> <table> --dbHost-<database> <host> --dbUsername-<database> <user> --dbPassword-<database> <password> --dbSchema-<database> <schema> [--dbPort-<database> <port>] [--clearCache]]

	(options that are described above are not described again)
	
	--server: instructs TextPipe to start an HTTP server
	
	--port <port>: specifies which port the server should run on (default 6000)
	
	--conns <n>: the number of simultaneous connections (default 1; will force to 1 unless the annotator specifies that it is capable of concurrent requests)
	
	--cache <database> <table>: Enables caching of results (requests will not be recomputed if they have been computed previously, based on the doc_id identifier, if given in the request)
	
	--clearCompute: instructs TextPipe to remove the caching table if it already exists, and then create it again (useful if the table doesn't exist already)

Example:

java -jar textpipe.jar --annotator uk.ac.man.pubtools.annotators.examples.HelloWorldAnnotator --server --port 8000

(then use a browser to go to to http://localhost:8000?n=5)

Raw server (for making services available to other TextPipe components):
java -jar textpipe.jar --annotator <annotator class name> --rawServer [--port <port>] [--conns <n>] [--params ...] [--cache <database> <table> --dbHost-<database> <host> --dbUsername-<database> <user> --dbPassword-<database> <password> --dbSchema-<database> <schema> [--dbPort-<database> <port>] [--clearCache]]
	
	(options that are described above are not described again)
	
	--rawServer: instructs TextPipe to start an HTTP server
	
java -jar textpipe.jar --annotator uk.ac.man.pubtools.annotators.examples.HelloWorldAnnotator --rawServer --port 8000

(then use the "compute" example above, but replace "--annotator uk..." with "--annotator uk.ac.man.textpipe.TextPipeClient --params host=localhost;port=8000"
