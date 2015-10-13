package uk.ac.man.biocontext.gold;

public class Token {
	String text;
	int start, end;
	public Token() {
		// TODO Auto-generated constructor stub
	}
	public Token(String s) {
		String [] parts = s.split("\t",-1);
		this.text = parts[2];
		this.start = Integer.parseInt(parts[1].split(" ")[1]);
		this.end = Integer.parseInt(parts[1].split(" ")[2]);
	}
	public void setText(String text) {
		this.text = text;
	}
	public String getText() {
		return text;
	}
	public void setStart(int start) {
		this.start = start;
	}
	public int getStart() {
		return start;
	}
	public void setEnd(int end) {
		this.end = end;
	}
	public int getEnd() {
		return end;
	}
}
