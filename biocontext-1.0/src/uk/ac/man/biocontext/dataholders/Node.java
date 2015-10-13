package uk.ac.man.biocontext.dataholders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import martin.common.Pair;

import uk.ac.man.biocontext.dataholders.Exception.NodeNotInSentenceException;
import uk.ac.man.biocontext.dataholders.Exception.ParseFailedException;

/**
 * This class processes McClosky-style parse-tree data in a tree form.
 * @author farzaneh
 *
 */
public class Node extends Participant{
	private static final int MAX_DIST = 1000;

	private static int id_counter = 0;
	private int id = id_counter++;

	private String tag;
	Map<String, String>	data;
	private Node 		parent;
	private List<Node> children;	
	//private Pair<Integer> index;

	private List<Node> leaves = null;

	public Node(String tag){
		this(tag, "");
	}

	public Node(String tag, String text){
		this.tag = tag;
		data = new HashMap<String, String>();
		setText(text);
		children = new ArrayList<Node>();
	}

	public int getID(){
		return id;
	}

	public Map<String, String> getData() {
		return data;
	}
	public void setData(Map<String, String> data) {
		this.data = data;
	}
	public String getTag() {
		return tag;
	}
	public void setTag(String tag) {
		this.tag = tag;
	}

	private void setText(String text) {
		this.data.put("text", text.replace("-LRB-", "(").replace("-RRB-", ")").
				replace("-LSB-", "[").replace("-RSB-", "]").
				replace("-LCB-", "{").replace("-RCB-", "}").
				replace("``", "\""));
	}
	public void addChild(Node e) {
		children.add(e);
		e.setParent(this);
	}
	public List<Node> getChildren() {
		return children;
	}
	public void setParent(Node parent) {
		this.parent = parent;
	}
	public Node getParent() {
		return parent;
	}
	public boolean isLeaf(){
		return children.isEmpty();
	}
	public List<Node> getItems(){
		List<Node> res = new ArrayList<Node>();
		res.add(this);
		if(!this.isLeaf()){
			for(Node child : this.children){
				res.addAll(child.getItems());
			}
		}
		return res;
	}
	public List<Node> getLeaves(){
		if (leaves  == null){
			List<Node> res = new ArrayList<Node>();
			for (Node i : this.getItems()){
				if (i.isLeaf()){
					res.add(i);
				}
			}
			//System.out.println(res.size());
			leaves = res;
			return res;
		}
		else {
			return leaves;
		}
	}

	public String toString(){
		return toString("");
	}
	private String toString(String indent){
		String res = tag;
		if (!this.data.isEmpty()){
			res = res + ' ' + this.data.toString();
		}
		res += " (" + id + ")";
		if (!isLeaf()){
			for (Node i : children){
				res = res + "\n" + indent + i.toString(indent + " ");
			}
		}
		return res;
	}

	public Node lowestCommonAncestor(Node node1, Node node2) throws NodeNotInSentenceException{
		if (node1.equals(node2)){
			return node1;
		}else{
			List<Node> path1 = this.path(node1);
			List<Node> path2 = this.path(node2);
			if (path1.size() == 0){
				//				System.out.println("path length is 0. sentence is " + this + "\nnode1 is " + node1);
				//				System.exit(0);
				throw new NodeNotInSentenceException("path length is 0. sentence is " + this + "\nnode1 is " + node1);
			}
			Node res = path1.get(0);
			for (int i = 0; i < Math.min(path1.size(), path2.size()); i++){
				if (path1.get(i).equals(path2.get(i))){
					res = path1.get(i);
				}
			}
			return res;
		}
	}
	public int distance(Node node1, Node node2) throws NodeNotInSentenceException{
		Node comPar = this.lowestCommonAncestor(node1, node2);
		if (comPar == null){
			throw new IllegalStateException("comPar is null. node1  = " + node1 + "node2 = " + node2 + "sentence = " + this);
		}
		int res = comPar.path(node1).size() + comPar.path(node2).size() - 2;
		return res;
	}

	public int surfaceDistance(Node node1, Node node2){
		if (this.getLeaves().contains(node1) && this.getLeaves().contains(node2)){
			int a = this.getLeaves().indexOf(node1);
			int b = this.getLeaves().indexOf(node2);
			return b-a;
		}else{
			return MAX_DIST;
		}
	}


	public Node(String s, Node parent){
		if(s.charAt(0) != '(' || s.charAt(s.length()-1) != ')'){
			throw new IllegalStateException("Parse tree string not well-formed: " + s);
		}
		this.parent = parent;
		this.data = new HashMap<String, String>();
		this.children = new ArrayList<Node>();

		s = s.substring(1, s.length()-1);

		//		System.out.println(s);
		if (!s.contains(" ")){
			throw new IllegalStateException(s);
		}
		this.tag = s.substring(0, s.indexOf(' '));
		s = s.substring(s.indexOf(' ') + 1);

		List<Pair<Integer>> childrenIndex = getChildrenIndex(s);
		if (childrenIndex.size() == 0){
			this.setText(s);
		}

		for (Pair<Integer> c : childrenIndex){
			this.addChild(new Node(s.substring(c.getX(), c.getY()), this));
		}

	}

	private List<Pair<Integer>> getChildrenIndex(String s) {
		List<Pair<Integer>> res = new ArrayList<Pair<Integer>>();
		int c = 0;
		int st = -1;
		for (int i = 0; i < s.length(); i++){
			if (s.charAt(i) == '('){
				c++;
			}else if (s.charAt(i) == ')'){
				c--;
			}
			if (c == 1 && st == -1){
				st = i;
			}
			if (c == 0 && st != -1){
				int en = i + 1;
				res.add(new Pair<Integer>(st, en));
				st = -1;
			}
			if (c < 0){
				throw new IllegalStateException("Parse tree string not well-formed: " + s);
			}
			//			System.out.println(c + " " + i);
		}
		return res;
	}

	public boolean contains(Node n){
		return this.getLeaves().contains(n);
	}

	/**
	 * 
	 * @param s a parsed sentence by McClosky or other similar parser
	 * @return the root of a parse tree
	 * @throws ParseFailedException 
	 */
	public static Node makeTree(String s) throws ParseFailedException{

		return new Node(s, (Node)null);
		//		if (s.contains("PARSE-FAILED")){
		//			throw new ParseFailedException(s);
		//		}
		//		else{
		//			String [] tok = s.split(" ");
		//			Node root = new Node(tok[0].replaceAll("^\\(*", "").replaceAll("\\)*$", ""));
		//			//root.index = new Pair<Integer>(0, tok[0].length()); 
		//			Node current = root;
		//
		//			for (String i : Misc.arraySlice(tok, 1)){
		//				String token = i;
		//				if (token.startsWith("(")){
		//					Node newNode = new Node(token.replaceAll("^\\(*", "").replaceAll("\\)*$", ""));
		//					current.addChild(newNode);
		//					current = newNode;
		//				}
		//				else{
		//					current.setText(token.replaceAll("^\\(*", "").replaceAll("\\)*$", ""));
		//					while(token.endsWith(")")){
		//						current = current.parent;
		//						token = token.substring(0, token.length() - 1);
		//					}
		//				}
		//			}
		//			if (root == null){
		//				throw new IllegalStateException("Failed to parse the sentence: " + s);
		//			}
		//			return root;
		//		}
	}

	public static Node findInTree(String parse, String substring) throws ParseFailedException{
		Node res = null;
		Node root = makeTree(parse);
		for (Node i : root.getLeaves()){
			String tmp = i.getData().get("text");
			if (substring.startsWith(tmp) || tmp.startsWith(substring)){
				res = i;
			}
		}
		return res;
	}

	/***
	 * Returns a list of nodes on the path from the object down to the given node.
	 * If no such path exists returns an empty list.
	 * Of course, a more efficient way to do this would be to go up from the node,
	 * but that would need more error handling.
	 * @param node
	 * @return
	 */
	public List<Node> path(Node node){
		List<Node> res = new LinkedList<Node>();

		if (this == node){
			res.add(node);
		}else{
			for(Node child : this.children){
				List<Node> tmp = child.path(node);
				if (!tmp.isEmpty()){
					tmp.add(0, this);
					res = tmp;
					break;
				}
			}
		}
		return res;
	}
	/**
	 * 
	 * @param node
	 * @param tag
	 * @return  the closest parent to node with tag in the tree denoted by self. If no such node exists, returns null.
	 * 
	 */
	private Node lowestWithTag(Node node, String tag){
		Node res = null;
		for (Node n : path(node)){
			if(n.getTag().equals(tag)){
				res = n;
			}
		}
		return res;
	}

	public boolean commands(Node node1, Node node2, String tag){
		Node lo = lowestWithTag(node1, tag);
		if (lo != null && !lo.path(node2).isEmpty()){
			return true;
		}
		else{
			return false;
		}
	}

	public boolean commands(Node node1, Node node2){
		return commands(node1, node2, "S");
	}

	public Node getRoot() {
		if (this.isRoot()){
			return this;
		}else{
			return this.getParent().getRoot();
		}
	}
	public boolean isRoot() {
		if (this.getParent() == null || this.parent.getTag().equals("FOREST")){
			return true;
		}else{
			return false;
		}		
	}

	public Node getClosest(Node pivot, List<Node> list) throws NodeNotInSentenceException{
		Node res = list.get(0);
		int dist = distance(pivot, res);

		for (Node n : list){
			if (distance(pivot, n) < dist){
				res = n;
				dist = distance(pivot, n);
			}
		}
		return res;
	}

	public Node pair2Node(String rawSentence, Pair<Integer> pair){
		//		System.out.println("Pair is " + pair);
		//		System.out.println("rawSentence:   " + rawSentence);
		//		System.out.println(rawSentence.length());
		List<Node> leaves = this.getLeaves();
		//		String remainder = rawSentence;
		int position = -1;
		int leafCount = 0;


		int debugStart = -1;

		if (pair.getX() == debugStart)
			for (Node n : leaves)
				System.out.println(n);

		//		System.out.println(rawSentence);
		//		for (Node l : leaves)
		//			System.out.print(l.getData().get("text") + " --- ");
		//		System.out.println();

		while (position <= pair.getX() && leafCount < leaves.size()){
			Node leaf = leaves.get(leafCount);
			if (pair.getX() == debugStart)
				System.out.println("** " + leafCount + ", " + leaf.getID() + ", " + leaf.getData().get("text") + ", " + position);
			if (position == pair.getX()){
				if (pair.getX() == debugStart)
					System.out.println("1: Returning " + leaf.getID());
				return leaf;
			}else{
				//				System.out.println("leafCount is now " + leafCount);
				//				System.out.println("remainder is now " + remainder);
				//				System.out.println("searchable text is '" + leaf.getData().get("text") + "'");
				//				System.out.println("position becomes " + position);

				//find the next occurence of this leaf text, starting at "position"
				int currLeafStart = rawSentence.indexOf(leaf.getData().get("text"), position);

				//if the next occurence is a bit away from the current search position and
				//the next leaf actually can be found before the current one, invalidate the current position
				//this is performed in order to combat some parsing tool errors where spurious tokens
				//very occasionally can be inserted
				if ((currLeafStart != -1) && (currLeafStart - position > 5) && leafCount < leaves.size() && rawSentence.indexOf(leaves.get(leafCount+1).getData().get("text"), position) < currLeafStart)
					currLeafStart = -1;

				//if the current leaf text could not be found (or was determined to  be invalid),
				//jump to the next leaf and continue search from there
				if (currLeafStart == -1){
					leafCount++;
					continue;
				}
				//					throw new IllegalStateException("pair2Node can't find the token with text: '" + rawSentence + "' \nand coordinates: " + pair);

				int currLeafEnd = currLeafStart + leaf.getData().get("text").length();

				if (pair.getX() == debugStart)
					System.out.println("****** " + pair + ": '" + rawSentence.substring(pair.getX(), pair.getY()) + "', " + currLeafStart + ", " + currLeafEnd + ", '" + leaf.getData().get("text") + "'");

				if (currLeafStart <= pair.getX() && currLeafEnd > pair.getX()){
					if (pair.getX() == debugStart)
						System.out.println("2: Returning " + leaf.getID());
					return leaf;
				}

				//				System.out.println("position is now " + position);
				//				System.out.println("nposition is now " + nextpos);

				position = currLeafEnd;

				//				remainder = rawSentence.substring(position);
				//				System.out.println();
			}

			leafCount++;
		}
		//System.out.println("position is now " + position);
		throw new IllegalStateException("pair2Node can't find the token with text of length " + rawSentence.length() + " and coordinates: " + pair);		
	}

	@Override
	public Node getNode() {
		//System.out.println("******* inside getNode");
		//System.out.println(this);
		return this;
	}
}

