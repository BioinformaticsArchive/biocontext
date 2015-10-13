package uk.ac.man.biocontext.tools;

import java.util.Iterator;
import java.util.List;

import martin.common.Pair;

public class SentenceSplitter implements Iterator<Pair<Integer>>, Iterable<Pair<Integer>>{
	private martin.common.SentenceSplitter ssp; 

	public SentenceSplitter(String text){
		ssp = new martin.common.SentenceSplitter(text);
	}

	public static List<Pair<Integer>> toList(String text){
		return martin.common.SentenceSplitter.toList(text);
	}

	public boolean hasNext() {
		return ssp.hasNext();
	}

	public Pair<Integer> next() {
		return ssp.next();
	}

	public void remove() {
		throw new IllegalStateException();
		
	}

	public Iterator<Pair<Integer>> iterator() {
		return this;
	}
	
	
	public static void main(String[] args){
		String s = "Concanavalin A-activated cycling  entity11  blasts from  entity10  that are transgenic for the  entity9  have increased sensitivity to  entity8 -mediated apoptosis, associated with a down-regulation of  entity7  in the  entity6 . In addition, blocking  entity5 , itself a positive regulator of  entity4 , with neutralizing antibodies renders the cells more susceptible to anti- entity3 -mediated apoptosis. In summary, our results provide compelling evidence that  entity2  protects against  entity1 -mediated death and is likely to be an important regulator of  entity0  homeostasis and tolerance.";
		
		for (Pair<Integer> p : new SentenceSplitter(s))
			System.out.println(p.toString());
	}
}
