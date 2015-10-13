package uk.ac.man.biocontext.gold;

public class Term {
	private String text;
	private int length;
	private int documentOffset;
	private String type;
	private String id;
	
	public Term(String text, int documentOffset, String type, String id) {
		this.text = text;
		this.length = text.length();
		this.documentOffset = documentOffset;
		this.type = type;
		this.id = id;
	}
	
	public String toString(){
		return id + "\t" + type + "\t" + documentOffset + "\t" + (documentOffset + length) + "\t_" + text + "_";
	}
	
	public void setDocumentOffset(int documentOffset) {
		this.documentOffset = documentOffset;
	}
	
	public String getId() {
		return id;
	}
	
	public int getDocumentOffset() {
		return documentOffset;
	}
	
	public int getLength() {
		return length;
	}
	public String getText() {
		return text;
	}
	public String getType() {
		return type;
	}
	public int getEndOffset(){
		return documentOffset + length;
	}

	public boolean overlaps(Term other) {
		int s1 = documentOffset;
		int e1 = getEndOffset();
		int s2 = other.getDocumentOffset();
		int e2 = other.getEndOffset();
		
		return (s1 >= s2 && s1 < e2) || (s2 >= s1 && s2 < e1);		
	}
	
	public Term clone(){
		return new Term(text,documentOffset,type,id);
	}
	
	public void setId(String id) {
		this.id = id;
	}
}
