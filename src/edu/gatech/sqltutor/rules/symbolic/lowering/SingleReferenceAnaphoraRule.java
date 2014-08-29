package edu.gatech.sqltutor.rules.symbolic.lowering;

import static edu.gatech.sqltutor.rules.datalog.iris.IrisUtil.literal;

import java.util.ArrayList;
import java.util.List;

import org.deri.iris.api.basics.IPredicate;
import org.deri.iris.api.basics.IQuery;
import org.deri.iris.api.basics.IRule;
import org.deri.iris.builtins.GreaterBuiltin;
import org.deri.iris.factory.Factory;
import org.deri.iris.storage.IRelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.gatech.sqltutor.rules.DefaultPrecedence;
import edu.gatech.sqltutor.rules.ITranslationRule;
import edu.gatech.sqltutor.rules.Markers;
import edu.gatech.sqltutor.rules.SymbolicState;
import edu.gatech.sqltutor.rules.datalog.iris.IrisUtil;
import edu.gatech.sqltutor.rules.datalog.iris.RelationExtractor;
import edu.gatech.sqltutor.rules.datalog.iris.StaticRules;
import edu.gatech.sqltutor.rules.datalog.iris.SymbolicFacts.TokenMap;
import edu.gatech.sqltutor.rules.datalog.iris.SymbolicPredicates;
import edu.gatech.sqltutor.rules.lang.StandardLoweringRule;
import edu.gatech.sqltutor.rules.symbolic.PartOfSpeech;
import edu.gatech.sqltutor.rules.symbolic.SymbolicException;
import edu.gatech.sqltutor.rules.symbolic.SymbolicType;
import edu.gatech.sqltutor.rules.symbolic.SymbolicUtil;
import edu.gatech.sqltutor.rules.symbolic.tokens.ISymbolicToken;
import edu.gatech.sqltutor.rules.symbolic.tokens.LiteralToken;
import edu.gatech.sqltutor.rules.symbolic.tokens.TableEntityRefToken;
import edu.gatech.sqltutor.rules.symbolic.tokens.TableEntityToken;
import edu.gatech.sqltutor.rules.symbolic.tokens.WhereToken;
import edu.gatech.sqltutor.rules.util.Literals;

public class SingleReferenceAnaphoraRule extends StandardLoweringRule implements
		ITranslationRule {
	private static final Logger _log = LoggerFactory.getLogger(SingleReferenceAnaphoraRule.class);
	
	private static final StaticRules staticRules = new StaticRules(SingleReferenceAnaphoraRule.class);
	
	private static final String PREFIX = "ruleSingleReferenceAnaphora_";
	private static final IPredicate PREDICATE = IrisUtil.predicate("ruleSingleReferenceAnaphora", 2);
	private static final IQuery QUERY = Factory.BASIC.createQuery(
		literal(PREDICATE, "?ref", "?where")
	);
	
	private static final IQuery getVerbPhrasesAfterWhere = Factory.BASIC.createQuery(
		literal(IrisUtil.predicate(PREFIX + "isVerbPhrase", 1), "?token"),
		literal(SymbolicPredicates.type, "?where", SymbolicType.WHERE),
		literal(GreaterBuiltin.class, "?token", "?where")
	);

	public SingleReferenceAnaphoraRule() {
	}

	public SingleReferenceAnaphoraRule(int precedence) {
		super(precedence);
	}
	
	@Override
	public boolean apply(SymbolicState state) {
//		_log.info(Markers.SYMBOLIC, "SingleReferenceAnaphoraRule.apply:\n{}", SymbolicUtil.prettyPrint(state.getRootToken()));
		return super.apply(state);
	}

	@Override
	protected boolean handleResult(IRelation relation, RelationExtractor ext) {
		if( relation.size() != 1 )
			throw new SymbolicException("Should only ever have one result, got: " + relation);
		ext.nextTuple();
		
		TokenMap tokenMap = state.getSymbolicFacts().getTokenMap();
		TableEntityRefToken ref = ext.getToken("?ref");
		WhereToken where = ext.getToken("?where");
		List<ISymbolicToken> verbPhrases = getVerbPhrasesAfterWhere();
		
		if( areAllPossessive(ref, verbPhrases) ) {
			_log.info(Markers.SYMBOLIC, "All verb phrases possessive for this reference: {}", ref);
			replaceAllPossessives(ref, verbPhrases);
			SymbolicUtil.replaceChild(where, Literals.whose());
		} else {
			_log.info(Markers.SYMBOLIC, "Possessive substitution not possible for ref: {}", ref);
		}
		
		return false;
	}
	
	private void replaceAllPossessives(TableEntityRefToken ref, List<ISymbolicToken> verbPhrases) {
		for( ISymbolicToken verbPhrase: verbPhrases ) {
			_log.info(Markers.SYMBOLIC, "Processing phrase:\n{}", SymbolicUtil.prettyPrint(verbPhrase));
			ISymbolicToken nounPhrase = getNounStartPhrase(verbPhrase);
			List<ISymbolicToken> children = nounPhrase.getChildren();
			ISymbolicToken token = children.get(0);
			
			if( token.getPartOfSpeech() == PartOfSpeech.DETERMINER ) {
				_log.info(Markers.SYMBOLIC, "Deleted determiner: {}", token);
				token.getParent().removeChild(token);
				token = children.get(0);
			}
			
			if( token.getPartOfSpeech() == PartOfSpeech.POSSESSIVE_PRONOUN ) {
				_log.info(Markers.SYMBOLIC, "Deleted possessive pronoun: {}", token);
				token.getParent().removeChild(token);
			} else {
				if( !(token instanceof TableEntityRefToken) )
					throw new SymbolicException("Expecting a reference token, not: " + token);
				_log.info(Markers.SYMBOLIC, "Deleting possessive reference: {}", token);
				token.getParent().removeChild(token);
				token = children.get(0);
				if( token.getPartOfSpeech() != PartOfSpeech.POSSESSIVE_ENDING )
					throw new SymbolicException("Expecting a possessive ending, not: " + token);
				token.getParent().removeChild(token);
			}
		}
	}
	
	private ISymbolicToken getNounStartPhrase(ISymbolicToken verbPhrase) {
		ISymbolicToken phrase = verbPhrase;
		while( true ) {
			List<ISymbolicToken> children = phrase.getChildren();
			if( children.size() == 0 )
				break;
			ISymbolicToken child = children.get(0);
			if( child.getPartOfSpeech() == PartOfSpeech.NOUN_PHRASE ) {
				phrase = child;
			} else {
				break;
			}
		}
		return phrase;
	}
	
	private boolean areAllPossessive(TableEntityRefToken ref, List<ISymbolicToken> verbPhrases) {
		TableEntityToken entity = ref.getTableEntity();
		if( verbPhrases.size() < 1 )
			return false;
		for( ISymbolicToken verbPhrase: verbPhrases ) {
			ISymbolicToken nounParent = getNounStartPhrase(verbPhrase);
			
			ISymbolicToken theRef = getPotentialReference(nounParent);
			
			if( theRef instanceof LiteralToken ) {
				if( theRef.getPartOfSpeech() != PartOfSpeech.POSSESSIVE_PRONOUN )
					return false;
			} else {
				TableEntityRefToken otherRef = (TableEntityRefToken)theRef;
				if( otherRef.getTableEntity() != entity )
					return false;
				ISymbolicToken after = SymbolicUtil.getFollowingToken(otherRef);
				if( after.getPartOfSpeech() != PartOfSpeech.POSSESSIVE_ENDING )
					return false;
			}
		}
		return true;
	}
	
	private ISymbolicToken getPotentialReference(ISymbolicToken nounParent) {
		List<ISymbolicToken> nounChildren = nounParent.getChildren();
		ISymbolicToken theRef = null;
		PartOfSpeech partOfSpeech = null;
		searchLoop:
		for( int i = 0, len = nounChildren.size(); i < len; ++i ) {
			theRef = nounChildren.get(i);
			partOfSpeech = theRef.getPartOfSpeech();
			switch( partOfSpeech ) {
			case DETERMINER:
				theRef = null;
				break;
			case NOUN_SINGULAR_OR_MASS:
			case NOUN_PLURAL:
			case POSSESSIVE_PRONOUN:
				break searchLoop;
			default:
				_log.warn(Markers.SYMBOLIC, "Unexpected token: {}", theRef);
				return null;
			}
		}
		return theRef;
	}
	
	private List<ISymbolicToken> getVerbPhrasesAfterWhere() {
		RelationExtractor ext = IrisUtil.executeQuery(getVerbPhrasesAfterWhere, state);
		ArrayList<ISymbolicToken> verbPhrases = new ArrayList<ISymbolicToken>(ext.getRelation().size());
		while( ext.nextTuple() ) {
			verbPhrases.add(ext.getToken("?token"));
		}
		return verbPhrases;
	}

	@Override
	protected IQuery getQuery() {
		return QUERY;
	}
	
	@Override
	public List<IRule> getDatalogRules() {
		return staticRules.getRules();
	}
	
	@Override
	protected int getDefaultPrecedence() {
		return DefaultPrecedence.PARTIAL_LOWERING - 1;
	}

}
