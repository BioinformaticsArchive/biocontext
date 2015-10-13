package uk.ac.man.biocontext.tools.negmole.svmWrapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import martin.common.InputStreamDumper;
import martin.common.StreamIterator;

import uk.ac.man.biocontext.dataholders.BindingEvent;
import uk.ac.man.biocontext.dataholders.RegulationEvent;
import uk.ac.man.biocontext.dataholders.SimpleEvent;
import uk.ac.man.biocontext.tools.negmole.Features;
import uk.ac.man.biocontext.tools.negmole.NegmoleBuild;
import uk.ac.man.biocontext.tools.negmole.NegmoleFeaturedEvent;
import uk.ac.man.biocontext.tools.negmole.Features.Classes;
import uk.ac.man.biocontext.tools.negmole.Features.NegOrSpec;
import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.textpipe.TextPipe;

public class SvmPerfWrapper {
	private List<NegmoleFeaturedEvent> allNg;
	private List<NegmoleFeaturedEvent> CIng;
	private List<NegmoleFeaturedEvent> CIIng;
	private List<NegmoleFeaturedEvent> CIIIng;

	private File perfDir;
	private File modelDir;

	private NegOrSpec negSpec;

	public SvmPerfWrapper(List<NegmoleFeaturedEvent> allNg, NegOrSpec negSpec, File perfDir, File perfModelDir) {
		this.allNg = allNg;
		this.perfDir = perfDir;
		this.modelDir = perfModelDir;
		this.negSpec = negSpec;
	}

	public void writeSvmPerf2File(File filesDir){
		this.CIng = new ArrayList<NegmoleFeaturedEvent>();
		this.CIIng = new ArrayList<NegmoleFeaturedEvent>();
		this.CIIIng = new ArrayList<NegmoleFeaturedEvent>();

		for (NegmoleFeaturedEvent nfe : allNg){
			if (SimpleEvent.EVENT_TYPES.contains(nfe.getEvent().getEveType())){
				CIng.add(nfe);
			}else if (BindingEvent.EVENT_TYPES.contains(nfe.getEvent().getEveType())){
				CIIng.add(nfe);
			}else if (RegulationEvent.EVENT_TYPES.contains(nfe.getEvent().getEveType())){
				CIIIng.add(nfe);
			}

		}
		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.out.println("Creating files... ");

		writeSvmPerfClass2File(CIng, new File(filesDir, "CI.dat"), Classes.I);
		writeSvmPerfClass2File(CIIng, new File(filesDir, "CII.dat"), Classes.II);
		writeSvmPerfClass2File(CIIIng, new File(filesDir, "CIII.dat"), Classes.III);
	}



	private void writeSvmPerfClass2File(List<NegmoleFeaturedEvent> ng, File file, Classes cl) {

		Set<Integer> allKeys = Features.categoryDims.get(cl.hashCode() + this.negSpec.hashCode());

		try{
			BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file));

			for (NegmoleFeaturedEvent fe : ng){
				//System.out.println(fe);
				Map<Integer, Integer> feDims2Vals = fe.getFeatureDims2Values();

				if (negSpec.equals(NegOrSpec.N))
					fileWriter.write(fe.isTargetNegated() ? "1 " : "0 ");
				else if(negSpec.equals(NegOrSpec.S))
					fileWriter.write(fe.isTargetSpeculated() ? "1 " : "0 ");
				else
					throw new IllegalStateException("Not recognised N/S : " + negSpec);

				int counter = 1; //Feature numbers must start from 1

				for (Integer featureNumber : allKeys){
					fileWriter.write(counter + ":"); 
					if (feDims2Vals.containsKey(featureNumber)){
						fileWriter.write(feDims2Vals.get(featureNumber) + " ");
					}
					else{
						fileWriter.write("0 ");
					}
					counter++;
					//					if (counter > 300){
					//						break;
					//					}
				}
				fileWriter.write("\n");

			}
			fileWriter.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	public void train(){
		File trainDir = Misc.getRandomTmpDir();
		trainDir.mkdir();
		modelDir.mkdir();
		
		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.out.println("writing features in the directory " + trainDir.getAbsolutePath());
		
		writeSvmPerf2File(trainDir);

		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.STATUS) >= 0)
			System.out.println("Training model " + new File(modelDir, negSpec.toString() + "MI.dat").getAbsolutePath());
		
		runLearn(new File(trainDir, "CI.dat"), new File(modelDir, negSpec.toString() + "MI.dat"));
		
		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.STATUS) >= 0)
			System.out.println("Training model " + new File(modelDir, negSpec.toString() + "MII.dat").getAbsolutePath());
		
		runLearn(new File(trainDir, "CII.dat"), new File(modelDir, negSpec.toString() + "MII.dat"));

		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.STATUS) >= 0)
			System.out.println("Training model " + new File(modelDir, negSpec.toString() + "MIII.dat").getAbsolutePath());
		
		runLearn(new File(trainDir, "CIII.dat"), new File(modelDir, negSpec.toString() + "MIII.dat"));
	}



	private void runLearn(File file, File file2) {
		String c1 = perfDir.getAbsolutePath() + "/svm_perf_learn -c 20.0 " + file.getAbsolutePath() + " " + file2.getAbsolutePath();
		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.out.println(c1);

		try{
			Process p1 = Runtime.getRuntime().exec(c1, null, perfDir);
			if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
				new Thread(new InputStreamDumper(p1.getInputStream(),System.out)).start();
			else
				new Thread(new InputStreamDumper(p1.getInputStream())).start();
			new Thread(new InputStreamDumper(p1.getErrorStream(),System.err)).start();
			p1.waitFor();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void test(File logDir) {
		File testDir = Misc.getRandomTmpDir();
		testDir.mkdir();
		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.out.println("writing features in the directory " + testDir.getAbsolutePath());

		writeSvmPerf2File(testDir);

		File outputDir = Misc.getRandomTmpDir();
		outputDir.mkdir();

		double res;

		String sem = NegmoleBuild.sem ? "sem_" : "";
		
		res = runClassify(new File(testDir, "CI.dat"), new File(modelDir, negSpec.toString() + "MI.dat"), new File(outputDir, "outI"));
		if (res != -1 && logDir != null)
			log(logDir, res, sem + negSpec.toString() + "I_", Features.categoryDims.get(Features.Classes.I.hashCode() + negSpec.hashCode()));

		res = runClassify(new File(testDir, "CII.dat"), new File(modelDir, negSpec.toString() + "MII.dat"), new File(outputDir, "outII"));
		if (res != -1 && logDir != null)
			log(logDir, res, sem + negSpec.toString() + "II_" , Features.categoryDims.get(Features.Classes.II.hashCode() + negSpec.hashCode()));

		res = runClassify(new File(testDir, "CIII.dat"), new File(modelDir, negSpec.toString() + "MIII.dat"), new File(outputDir, "outIII"));
		if (res != -1 && logDir != null)
			log(logDir, res, sem + negSpec.toString() + "III_", Features.categoryDims.get(Features.Classes.II.hashCode() + negSpec.hashCode()));

		parseClassified(new File(outputDir, "outI"), CIng);
		parseClassified(new File(outputDir, "outII"), CIIng);
		parseClassified(new File(outputDir, "outIII"), CIIIng);

		Misc.delete(testDir);
		Misc.delete(outputDir);
	}

	private void log(File logDir, double fScore, String name, Set<Integer> set) {
		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
		System.out.println(logDir.getAbsolutePath() + " " + fScore + " " + set.size());

		File f = new File(logDir, name + fScore + ".txt");

		TreeMap<String, Integer> features = new TreeMap<String, Integer>();

		for (Integer i : set){
			String n = Features.allPossibleValuesReverse.get(i);
			if (n == null)
				throw new IllegalStateException("**** WARNING: RECEIVED NULL F FOR " + i);

			if (n.contains(".")){
				String t = n.substring(0,n.lastIndexOf('.'));
				if (t.endsWith("."))
					t = t.substring(0, t.length()-1);


				if (features.containsKey(t))
					features.put(t, features.get(t)+1);
				else
					features.put(t, 1);

			} else {
				features.put(n,0);
			}
		}

		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(f));
			for (String feature : features.keySet())
				if (features.get(feature) > 0)
					outStream.write(feature + "." + features.get(feature) + "\n");
				else
					outStream.write(feature + "\n");
			outStream.close();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private void parseClassified(File outputFile, List<NegmoleFeaturedEvent> events) {
		StreamIterator st = new StreamIterator(outputFile);
		int counter = 0;

		for(String target : st){
			Double val = Double.parseDouble(target);
			events.get(counter++).setTarget(val>0, negSpec);
		}
	}

	private double runClassify(File featureFile, File modelFile, File outputFile) {
		String c = perfDir.getAbsolutePath() + "/svm_perf_classify " + featureFile.getAbsolutePath() + " " + modelFile.getAbsolutePath() + " " + outputFile.getAbsolutePath();

		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.out.println(c);

		try{
			StringBuffer learnSB = new StringBuffer();

			Process p = Runtime.getRuntime().exec(c, null, perfDir);
			new Thread(new InputStreamDumper(p.getInputStream(), learnSB)).start();
			if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.WARNINGS) >= 0)
				new Thread(new InputStreamDumper(p.getErrorStream(), System.err)).start();
			p.waitFor();


			if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0) {
				System.out.println(learnSB);
			}

			int s = learnSB.indexOf("F1       : ")+11;
			if (s==10)
				return -1; // -1 if the string is not found, and then we add 11 for the length of the sting.

			if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0){
				System.out.println(modelFile.getName());
				for (String l : learnSB.toString().split("\n"))
					if (l.contains("Recall") || l.contains("Precision") || l.contains("F1") || l.contains("Accuracy"))
						System.out.println(l);
				System.out.println();
			}

			double res = Double.parseDouble(learnSB.substring(s, learnSB.indexOf("\n", s)));

			return res; 

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
			return -1;
		}
	}

	//	public static void classify(File testFile, File modelFile, File outputFile, File perfDir){
	//
	//		String c = perfDir.getAbsolutePath() + "/svm_perf_classify " + testFile.getAbsolutePath() + " " + modelFile.getAbsolutePath() + " " + outputFile.getAbsolutePath();
	//		//System.out.println("Classifying...");
	//		//System.out.println(c);
	//		try{
	//			Process p = Runtime.getRuntime().exec(c, null, perfDir);
	//			//new Thread(new InputStreamDumper(p.getInputStream(),System.out)).start();
	//			//new Thread(new InputStreamDumper(p.getErrorStream(),System.err)).start();
	//			p.waitFor();
	//		} catch (Exception e){
	//			System.err.println(e);
	//			e.printStackTrace();
	//			System.exit(-1);
	//		}
	//		System.out.println("Done.");
	//	}

	//	public void classify(){
	//		File testFile = ap.getFile("perfTestFile", Misc.getRandomTmpDir());
	//		writeSvmPerf2File(allNg, testFile, "N");
	//
	//		String c = perfDir.getAbsolutePath() + "/svm_perf_classify " + testFile.getAbsolutePath() + " " + modelFile.getAbsolutePath() + " output";
	//		try{
	//			Process p = Runtime.getRuntime().exec(c, null, perfDir);
	//			new Thread(new InputStreamDumper(p.getInputStream(),System.out)).start();
	//			new Thread(new InputStreamDumper(p.getErrorStream(),System.err)).start();
	//			p.waitFor();
	//		} catch (Exception e){
	//			System.err.println(e);
	//			e.printStackTrace();
	//			System.exit(-1);
	//		}
	//
	//	}

	public static void readSvmPerfOutputFile(List<NegmoleFeaturedEvent> ng,
			File outputNegTempFile, String type) {

		StreamIterator si = new StreamIterator(outputNegTempFile);


		int c = 0;
		for (String s : si){
			if (Double.parseDouble(s) <= 0){
				if (type.equals("N")){
					ng.get(c).setTargetNegated(false);
				}else if (type.equals("S")){
					ng.get(c).setTargetSpeculated(false);
				} else throw new IllegalStateException("readSvmPerfOutputFile called with type other than N or S: " + type);
			}else if (Double.parseDouble(s) > 0){
				if (type.equals("N")){
					ng.get(c).setTargetNegated(true);
				}else if (type.equals("S")){
					ng.get(c).setTargetSpeculated(true);
				} else throw new IllegalStateException("readSvmPerfOutputFile called with type other than N or S: " + type);
			}
			c++;
		}
	}



}
