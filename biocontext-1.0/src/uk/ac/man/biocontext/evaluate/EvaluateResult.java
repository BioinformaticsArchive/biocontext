package uk.ac.man.biocontext.evaluate;

import java.util.HashMap;
import java.util.Map;

public class EvaluateResult {
	public enum EvalType {TP, FP, FN}
	
	private EvalType type;
	public void setType(EvalType type) {
		this.type = type;
	}

	private Map<String,String> entry;
	public void setS(int s) {
		this.s = s;
	}

	public void setE(int e) {
		this.e = e;
	}

	private int s, e;
	
	public EvaluateResult(EvalType type, Map<String, String> entry, int s, int e) {
		super();
		this.type = type;
		this.entry = entry;
		this.s = s;
		this.e = e;
	}
	
	public EvaluateResult(EvalType type, Map<String, String> entry, String s, String e) {
		this(type, entry, s != null ? Integer.parseInt(s) : -1, e != null ? Integer.parseInt(e) : -1);
	}
	
	public EvalType getType() {
		return type;
	}
	
	public Map<String, String> getEntry() {
		return entry;
	}
	
	public String toString(){
		return type.toString();
	}

	public int getS() {
		return s;
	}

	public int getE() {
		return e;
	}

	public Map<String, String> getInfo() {
		return info;
	}

	private Map<String,String> info = new HashMap<String,String>();

}
