# -*- coding: utf-8 -*-

import pdb

from . import BaseFeatureExtracter, register_extracter


@register_extracter('nameinuttr')
class NameInUttrFeatureExtracter(BaseFeatureExtracter):
    """`Name in utterance' feature extracter.
    
    This binary feature represents whether the name of the character appears in 
    the sentence of the utterance. Except for some rare scenarios (e.g., ``My 
    name is ...'' or using selfs name as the replacement of ``I'' in 
    utterances), the characters appear in the utterance are usually not the 
    speaker, for example, ``How are you, Kate?''.
    """
    
    def __init__(self, **kargs):
        super(NameInUttrFeatureExtracter, self).__init__(**kargs)

    def extract(self, ret, paragraph_num, paragraph_has_quote, 
                paragraph_quote_token_id, character_appear_token_id, 
                character_num, characters, **kargs):
        """Extract `name in utterance' feature for a chapter.
        
        Args:
            ret: 2-D list of directories to save features.
            paragraph_num: Number of paragraphs.
            paragraph_has_quote: Whether the paragraph contains a quote.
            paragraph_quote_token_id: Start and end token IDs of quotes in paragraphs. Stored alternatively by start and end ids.
            character_appear_token_id: The token IDs of character mentions.
            character_num: Number of characters.
            characters: Characters of the chapter
        """
        for i in range(paragraph_num):
            if paragraph_has_quote[i]:
                for j in range(character_num):
                    char = sorted(characters.keys())[j]
                    appear = 0
                    pid = 0
                    while pid < len(paragraph_quote_token_id[i]):
                        startId = paragraph_quote_token_id[i][pid]
                        endId = paragraph_quote_token_id[i][pid+1]
                        for cid in character_appear_token_id[char]:
                            if (startId <= cid and endId >= cid):
                                appear = 1
                        pid += 2
                    ret[i][j]['nameinuttr'] = appear
                    #ret[i][j]['nameinuttr'] = 0

    @classmethod
    def build_extracter(cls, args):
        """Build a new NameInUttrFeatureExtracter instance."""
        return cls()
