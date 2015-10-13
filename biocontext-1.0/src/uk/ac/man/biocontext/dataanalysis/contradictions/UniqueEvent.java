package uk.ac.man.biocontext.dataanalysis.contradictions;

import java.util.List;

public class UniqueEvent{
	private List<String> data;
	private int countDocs;
	private double sumConfidences;
	private int hash;
	
	public UniqueEvent(List<String> data, int countDocs,
			double sumConfidences, int hash) {
		super();
		this.data = data;
		this.countDocs = countDocs;
		this.sumConfidences = sumConfidences;
		this.hash = hash;
	}

	public List<String> getData() {
		return data;
	}

	public int getCountDocs() {
		return countDocs;
	}

	public double getSumConfidences() {
		return sumConfidences;
	}

	public int getHash() {
		return hash;
	}

	public void incrementCountDocs(int count) {
		countDocs += count;		
	}

	public void incrementSumConfidences(double confidence) {
		sumConfidences += confidence;
	}
	
	public String toString(){
		String s = "" + hash + ", " + countDocs + ", " + sumConfidences;
		for (String d : data)
			s += ", " + d;
		return s;
	}
}
