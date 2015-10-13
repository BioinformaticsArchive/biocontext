package uk.ac.man.farzin.tools.negmole;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;
import uk.ac.man.farzin.dataholders.Node;
import uk.ac.man.farzin.dataholders.Exception.ParseFailedException;

public class NegmoleTest {
	
	
	
	@Ignore
	@Test
	public void getCueOccurancesTest() throws ParseFailedException{
		String s = "(S (N (P (V w1))) (Q (R not) (S w3)))";
		Node r = Node.makeTree(s);
		String res = "";
//		for (Node i : Negmole.getCueOccurances(r, Negation.NEGATION_CUES)){
//			res += i.getData().get("text");
//		}
		assertEquals("not", res);
	}
}
