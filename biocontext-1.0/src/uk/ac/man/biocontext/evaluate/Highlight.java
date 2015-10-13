package uk.ac.man.biocontext.evaluate;

public class Highlight implements Comparable<Highlight>{
	private int start, end;
	private String color;
	private String text;
	private String URL;

	public Highlight(String color, int start, int end, String text, String URL) {
		super();
		this.color = color;
		this.end = end;
		this.start = start;
		this.text = text;
		this.URL = URL;
	}

	public int getStart() {
		return start;
	}

	public int getEnd() {
		return end;
	}

	public String getColor() {
		return color;
	}
	
	public String getText() {
		return text;
	}

	@Override
	public int compareTo(Highlight h) {
	//NOTE: this is reversed to what is normally used
		if (start + end == h.start + h.end)
			return 0;
		return start + end < h.start + h.end ? 1 : -1;
	}
	
	public String toString(){
		return "(" + start + "," + end + "), " + color + ", " + text;
	}

	public boolean overlaps(Highlight h) {
		int s1 = start;
		int e1 = end;
		int s2 = h.getStart();
		int e2 = h.getEnd();
		
		return (s1 >= s2 && s1 < e2) || (s2 >= s1 && s2 < e1);		
	}

	public String getURL() {
		return URL;
	}

	public void setText(String text) {
		this.text = text;
	}
}
