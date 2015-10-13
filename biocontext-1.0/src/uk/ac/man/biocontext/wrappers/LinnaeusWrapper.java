package uk.ac.man.biocontext.wrappers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.networking.SimpleClientMatcher;
import uk.ac.man.textpipe.Annotator;

public class LinnaeusWrapper extends Annotator {

	private SimpleClientMatcher client;
	public static final String[] outputFields = new String[]{"id","entity_id","entity_start","entity_end","entity_term","entity_group"};

	@Override
	public void init(Map<String, String> data) {
		if (data == null){
			System.err.println("The LINNAEUS wrapper needs 'host' and 'port' parameters; send with --params host=<host>,port=<port>");
			System.exit(0);
		}

		String host = data.get("host");
		int port = Integer.parseInt(data.get("port"));

		this.client = new SimpleClientMatcher(new String[]{host}, new int[]{port});
	}

	public LinnaeusWrapper(){

	}

	public LinnaeusWrapper(String host, int port){
		this.client = new SimpleClientMatcher(new String[]{host}, new int[]{port});
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		List<Mention> mentions = null;

		if (data.containsKey("doc_text"))
			mentions = client.match(data.get("doc_text"), data.get("doc_id"));
		else
			return new ArrayList<Map<String,String>>();

		List<Map<String,String>> res = new ArrayList<Map<String,String>>();

		int c = 0;

		for (Mention m : mentions){
			if (okToAdd(m)){
				Map<String,String> map = new HashMap<String, String>();

				map.put("entity_id", m.getIdsToString());
				map.put("entity_start", ""+m.getStart());
				map.put("entity_end", ""+m.getEnd());
				map.put("entity_term", ""+m.getText());


				String group = null;
				if (m.getComment().indexOf("group: ") != -1){
					int s = m.getComment().indexOf("group: ") + 7;
					int e  = m.getComment().indexOf(",",s);
					if (e != -1){
						group = m.getComment().substring(s,m.getComment().indexOf(",",s));
					} else {
						group = m.getComment().substring(s);
					}
				}

				map.put("entity_group", group);
				map.put("id",""+c++);

				res.add(map);
			}
		}

		return res;
	}

	private boolean okToAdd(Mention m) {
		if (m.getMostProbableID().equals("anat:30"))
			return false;

		return true;
	}

	@Override
	public boolean isConcurrencySafe() {
		return true;
	}
}
