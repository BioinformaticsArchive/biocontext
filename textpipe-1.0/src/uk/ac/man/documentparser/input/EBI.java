package uk.ac.man.documentparser.input;

import java.io.File;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Node;

import martin.common.xml.XPath;

import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.dataholders.ExternalID;
import uk.ac.man.documentparser.dataholders.ExternalID.Source;

public class EBI implements DocumentIterator {

	private Iterator<Node> docs;

	public EBI(File file){
		try{
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			org.w3c.dom.Document doc = db.parse(file);

			this.docs = XPath.getNodeList("PubmedArticle", doc.getDocumentElement()).iterator();

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}		
	}

	@Override
	public void skip() {
		if (!docs.hasNext())
			throw new NoSuchElementException();
		docs.next();
	}

	@Override
	public boolean hasNext() {
		return docs.hasNext();
	}

	@Override
	public Document next() {
		Node d = docs.next();
		String id = XPath.getNode("MedlineCitation/PMID", d).getTextContent();

		String title = XPath.getNode("MedlineCitation/Article/ArticleTitle", d) != null ? XPath.getNode("MedlineCitation/Article/ArticleTitle", d).getTextContent() : null;
		String abs = XPath.getNode("MedlineCitation/Article/Abstract/AbstractText", d) != null ? XPath.getNode("MedlineCitation/Article/Abstract/AbstractText", d).getTextContent() : null;

		if (title.length() == 0)
			title = null;

		return new Document(id,title,abs,null,null,null,null,null,null,null,null,null,null,null,new ExternalID(id,Source.MEDLINE));
	}

	@Override
	public void remove() {
		docs.remove();
	}

	@Override
	public Iterator<Document> iterator() {
		return this;
	}
}
