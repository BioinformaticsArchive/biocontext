package uk.ac.man.biocontext.wrappers.genener;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import gnat.filter.Filter;
import gnat.representation.Context;
import gnat.representation.GeneRepository;
import gnat.representation.IdentificationStatus;
import gnat.representation.RecognizedEntity;
import gnat.representation.TextRepository;

public class RemoveNonRepGenesFilter implements Filter {

	@Override
	public void filter(Context context, TextRepository arg1, GeneRepository geneRepository) {
		Iterator<RecognizedEntity> unidentifiedGeneNames = context.getUnidentifiedEntities().iterator();

		while (unidentifiedGeneNames.hasNext()){
			RecognizedEntity recognizedGeneName = unidentifiedGeneNames.next();

			IdentificationStatus identificationStatus = context.getIdentificationStatus(recognizedGeneName);
			Set<String> geneIdCandidates = identificationStatus.getIdCandidates();

			Iterator<String> it = geneIdCandidates.iterator();

			Set<String> toRemove = new HashSet<String>();

			while (it.hasNext()) {
				String gid = it.next();
				if (geneRepository.getGene(gid) == null){
					toRemove.add(gid);
				}
			}

			for (String gid : toRemove){
				identificationStatus.removeIdCandidate(gid);
			}
		}
	}
}