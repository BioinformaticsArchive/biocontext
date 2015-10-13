package uk.ac.man.farzin.dataholders;


import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import martin.common.Pair;

import org.junit.*;

import uk.ac.man.farzin.dataholders.Exception.NodeNotInSentenceException;
import uk.ac.man.farzin.dataholders.Exception.ParseFailedException;


public class NodeTest {

	String s;
	Node r, node1, node2, w1, w2, w3;
	
	@Before
	public void setUp() throws ParseFailedException{
	s = "(S (N (P (V w1))) (Q (R w2) (S w3)))";
	r = Node.makeTree(s);
	node1 = r.getChildren().get(0);
	node2 = r.getChildren().get(1);
	w1 = r.getChildren().get(0).getChildren().get(0).getChildren().get(0);
	w2 = r.getChildren().get(1).getChildren().get(0);
	w3 = r.getChildren().get(1).getChildren().get(1);	
	}
	
	@Test
	public void makeTreeTest(){
		String st = "S (0)\nN (1)\n P (2)\n  V {text=w1} (3)\nQ (4)\n R {text=w2} (5)\n S {text=w3} (6)";
		assertEquals(st, r.toString());
		}

	@Test
	public void getParentTest(){
		assertTrue(r.getChildren().get(0).getParent().isRoot());
	}

	@Test
	public void rootParentIsNullTest(){
		assertNull(r.getParent());
	}
	
	@Test
	public void pathTest(){
		List<Node> p = r.path(r.getChildren().get(0).getChildren().get(0));
		String st = "[S (21)\nN (22)\n P (23)\n  V {text=w1} (24)\nQ (25)\n R {text=w2} (26)\n S {text=w3} (27), N (22)\nP (23)\n V {text=w1} (24), P (23)\nV {text=w1} (24)]";
		System.out.println(p.toString());
		assertEquals(st, p.toString());
	}
	
	@Test
	public void regularExpressionTest(){
		String t = s.replaceAll("^\\(*", "").replaceAll("\\)*$", "");
		assertEquals("S (N (P (V w1))) (Q (R w2) (S w3", t);
	}
	
	@Test
	public void lowestCommonAncestorTest0() throws NodeNotInSentenceException{
		assertEquals(r, r.lowestCommonAncestor(node1, node2));
	}
	
	@Test
	public void lowestCommonAncestorTest1() throws NodeNotInSentenceException{
		assertEquals(node1, r.lowestCommonAncestor(node1, node1));
	}
	
	@Test
	public void distanceTest() throws NodeNotInSentenceException{
		assertEquals(0, r.distance(r, r));
		assertEquals(1, r.distance(r, node1));
		assertEquals(1, r.distance(r, node2));
		assertEquals(2, r.distance(node1, node2));
		assertEquals(2, r.distance(r, node1.getChildren().get(0)));
		assertEquals(3, r.distance(r, node1.getChildren().get(0).getChildren().get(0)));
		assertEquals(4, r.distance(node2, node1.getChildren().get(0).getChildren().get(0)));
		assertEquals(5, r.distance(node2.getChildren().get(0), node1.getChildren().get(0).getChildren().get(0)));
	}
	
	@Test
	public void surfaceDistanceTest(){
		assertEquals(0, r.surfaceDistance(w1, w1));
		assertEquals(1, r.surfaceDistance(w1, w2));
		assertEquals(2, r.surfaceDistance(w1, w3));
		assertEquals(1, r.surfaceDistance(w2, w3));
		assertEquals(0, r.surfaceDistance(w2, w2));
		assertEquals(0, r.surfaceDistance(w3, w3));
		assertEquals(-1, r.surfaceDistance(w3, w2));
		assertEquals(-1, r.surfaceDistance(w2, w1));
		assertEquals(-2, r.surfaceDistance(w3, w1));
		
	}
	
	@Test
	public void commandsTest(){
		assertTrue(r.commands(w1, w2, "S"));
		assertFalse(r.commands(w1, w2, "N"));
		assertFalse(r.commands(w3, w2, "S"));
		assertTrue(r.commands(w2, w3, "S"));
	}
	
	@Test
	public void lowestCommonAncestorTest() throws NodeNotInSentenceException{
		assertEquals(r, r.lowestCommonAncestor(r, r));
		assertEquals(w1, r.lowestCommonAncestor(w1, w1));
		assertEquals(w2, r.lowestCommonAncestor(w2, w2));
		assertEquals(w3, r.lowestCommonAncestor(w3, w3));
		assertEquals(node2, r.lowestCommonAncestor(w2, w3));
		assertEquals(node2, node2.lowestCommonAncestor(w2, w3));
		assertEquals(node2, node2.lowestCommonAncestor(w3, w2));
		assertEquals(r, r.lowestCommonAncestor(w1, w2));
		assertEquals(r, r.lowestCommonAncestor(node2, w1));

	}
	
	@Test
	public void getClosestTest() throws NodeNotInSentenceException{
		List<Node> l;
		
		l = Arrays.asList(w1, w2);
		assertEquals(w1, r.getClosest(w1, l));
		assertEquals(w2, r.getClosest(w3, l));
		
		l = Arrays.asList(w1, w3);
		assertEquals(w3, r.getClosest(w2, l));
		
		l = Arrays.asList(w2, w3, node1, node2);
		assertEquals(node1, r.getClosest(w1, l));
		
		l = Arrays.asList(w1, w3, node1, node2);
		assertEquals(node2, r.getClosest(w2, l));
	}

	@Test
	public void jonathanGotArithmeticTalentTest(){
		assertEquals(4, 2*2);
	}
	
	@Test
	public void pair2NodeTest() throws ParseFailedException{
		Pair<Integer> p1 = new Pair<Integer>(0, 1);
		Pair<Integer> p2 = new Pair<Integer>(3, 4);
		Pair<Integer> p3 = new Pair<Integer>(6, 7);
		Node n1 = r.pair2Node("w1 w2 w3", p1);
		assertEquals(w1, n1);
		Node n2 = r.pair2Node("w1 w2 w3", p2);
		assertEquals(w2, n2);
		Node n3 = r.pair2Node("w1 w2 w3", p3);
		assertEquals(w3, n3);
		
		Pair<Integer> p4 = new Pair<Integer>(9, 10);
		Node n4 = r.pair2Node("   w1 w2 w3", p4);
		assertEquals(w3, n4);
		
		Pair<Integer> p5 = new Pair<Integer>(11, 12);
		Node n5 = r.pair2Node("   w1 (w2) w3", p5);
		assertEquals(w3, n5);
		
		Pair<Integer> p6 = new Pair<Integer>(12, 13);
		Node n6 = r.pair2Node("   w1 (w2)  w3.", p6);
		assertEquals(w3, n6);
		
		String text = "Activation-dependent transcriptional regulation of the human Fas promoter requires NF-kappaB p50-p65 recruitment.";
		String parse = "(S1 (S (NP (NP (JJ Activation-dependent) (JJ transcriptional) (NN regulation)) (PP (IN of) (NP (DT the) (VBN human Fas promoter) (NN oter)))) (VP (VBZ requires) (NP (JJ NF-kappaB) (NN p50-p65) (NN recruitment))) (. .)))";
		Node root = Node.makeTree(parse);
		
		Pair<Integer> p7 = new Pair<Integer>(11, 20);
		Node n7 = root.pair2Node(text, p7);
		assertNotNull(n7);
		assert(n7.getData().get("text").equals("Activation-dependent"));
		assertEquals(root.getChildren().get(0).getChildren().get(0).getChildren().get(0).getChildren().get(0), n7);
		
	}

	@Test
	public void semanticTokenTest() throws ParseFailedException{
		String s2 = "(S (N (P (V w1 www)) (Q (R w2) (S w3))))";
		
		Node r2 = new Node(s2, (Node)null);
		assertEquals("w1 www", r2.getChildren().get(0).getChildren().get(0).getChildren().get(0).getData().get("text"));
	}
}

