package uk.ac.man.biocontext.wrappers.genener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import martin.common.Pair;

import banner.processing.PostProcessor;
import banner.tagging.CRFTagger;
import banner.tokenization.Tokenizer;

import uk.ac.man.biocontext.tools.SentenceSplitter;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.matching.Matcher;
import banner.BannerProperties;
import banner.Sentence;

public class BannerMatcher extends Matcher {

	private Tokenizer tokenizer;
	private CRFTagger tagger;
	private PostProcessor postProcessor;

	public BannerMatcher(File propertiesFile, File modelFile, Logger logger){
		BannerProperties properties = BannerProperties.load(propertiesFile.getAbsolutePath());
		this.tokenizer = properties.getTokenizer();
		try {
			if (logger != null)
			logger.info("%t: Loading CRF tagger...\n");
			this.tagger = CRFTagger.load(modelFile, properties.getLemmatiser(), properties.getPosTagger());
			if (logger != null)
			logger.info("%t: CRF tagger loaded.\n");
		} catch (IOException e) {
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		this.postProcessor = properties.getPostProcessor();
	}

	@Override
	public List<Mention> match(String text, Document doc) {
		text = text.replace('\r', ' ');
		text = text.replace('\n', ' ');
		
		SentenceSplitter ssp = new SentenceSplitter(text);
		
		List<Mention> matches = new ArrayList<Mention>();

		for (Pair<Integer> p : ssp){
			String sentenceString = text.substring(p.getX(), p.getY());
			
			if (sentenceString.trim().length() > 0){
				Sentence sentence = new Sentence(sentenceString);
				tokenizer.tokenize(sentence);
				tagger.tag(sentence);
				if (postProcessor != null)
					postProcessor.postProcess(sentence);

				List<banner.tagging.Mention> mentions = sentence.getMentions();

				if (mentions.size() > 0){
					for (banner.tagging.Mention m : mentions){
						int s = m.getStartChar();
						int e = m.getEndChar();
						String t = sentenceString.substring(s,e);
						
						Mention match = new Mention(new String[]{"0"}, s + p.getX(), e + p.getX(), t);
						if (doc != null)
							match.setDocid(doc.getID());
						matches.add(match);
					}
				}

			}
		}
		
		return matches;
	}
}