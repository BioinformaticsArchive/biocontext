BioContext - system for extraction and contextualization of biomedical events

This system builds heavily on the TextPipe library (see textpipe.txt for documentation). Using TextPipe, a number of text-mining components have been wrapped or developed, and connected to each other. An overview of how the components are connected to each other can be seen in fig1.png. Each box - and circle - is represented by a TextPipe component. A more complete flowchart, with the class name of each component, can be seen in fig2.png (note that the classnames all start with uk.ac.man.biocontext; the numbers in parantheses are the port numbers we used during the development of BioContext - these are still used in the sourcecode in different places). Note that the Linnaeus wrappers require local LINNAEUS NER servers - this should be a LINNAEUS species server in the case of the species component (downloadable from linnaeus.sourceforge.net) and an anatomical NER server in the case of the anatomical NER component (downloadble from getm-project.sourceforge.net). 

In addition to the BioContext and TextPipe libraries, many of the components require additional installations - for example, the GDepWrapper component, wrapping the Genia dependency parser, require a functional installation of GDep (the GDepWrapper is initialized with the location of GDep in the local file system, which allows it to call the tool). Due to licensing reasons, these tools cannot be provided together with BioContext, but rather have to be downloaded and set up manually.

While it certainly is possible to set up the BioContext pipeline such that calls to the final component (util.annotators.UniverseWrapper, which brings all data together) propagated to the dependencies, we performed computations in the reverse order instead for performance reasons. This is reflected in how the components access data - most will assume that dependent components have all been already run, and that results from them are available in databases. This can be changed relatively easily through editing of the source code - any such assumptions will exist in the init() function of the components, where connections to underlying components are set up. For example, see uk.ac.man.biocontext.wrappers.NegMoleWrapper.init(...), where three components are set up. These are set up as "PrecomputedAnnotator", which assume that results already have been computed. They could relatively easily be changed to uk.ac.man.textpipe.TextPipeClient components though, that instead will request results in real time.

If you would like to re-run the BioContext pipeline in full, we would recommend that you first prepare a dozen or so test documents. Then set up and test each annotator in turn, starting with those that have no dependencies. If you would like to use only a single component for your own project, then we would recommend that you start those components in the TextPipe server mode, and connect to them through either normal web request (in the case of the TextPipe HTTP server mode) or through TextPipe client calls (in the case of the raw TextPipe server mode).

Command-line arguments to TextPipe and BioContext can also be provided in property files, that are loaded with the "--properties <file>" command. Such property files for the various components we have used are available in the doc/profiles directory. While they will need editing to reflect your local circumstances, it should simplify things.

For example, running "java -jar biocontext.jar --properties db.conf linnaeus-species.conf --server --conns 4 --port 8080" will load database configuration settings and LINNAEUS, with its configuration settings, and serve LINNAEUS NER service requests on port 8080. linnaeus-species.conf need to be edited to reflect an internal LINNAEUS server though, and db.conf need to be edited to reflect your local database installation.

If you run into any problems, don't hesitate to contact us (contact details are available at biocontext.org). 

----

Third-party libraries:

Note that due to licensing restrictions, several third-party libraries could not be distributed together with this software. These libraries will need to be downloaded separately if you need functionality that is provided by them.

LIBRARIES INCLUDED: 
linnaeus.jar: BSD license
textpipe.jar: BSD license
gnat.jar: BSD license
BANNER (src): Common public license

LIBRARIES NOT INCLUDED:
dragontool.jar: www.dragontoolkit.org
eventmine.jar: Tokyo lab event miner - contact them
genetukit.jar: http://www.qanswers.net/GeneTUKit/
heptag.jar: www.dragontoolkit.org
junit-4.8.1.jar: www.junit.org
mallet.jar: mallet.cs.umass.edu
medpost.jar: sourceforge.net/projects/medpost
sptoolkit.jar: http://text0.mib.man.ac.uk:8080/scottpiao/sent_detector
stanford parser: http://nlp.stanford.edu/software/lex-parser.shtml#Download