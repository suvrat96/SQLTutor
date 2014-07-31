package edu.gatech.sqltutor.rules.lang;

import static edu.gatech.sqltutor.rules.datalog.iris.IrisUtil.literal;

import java.util.EnumSet;
import java.util.List;

import org.deri.iris.api.basics.IPredicate;
import org.deri.iris.api.basics.IQuery;
import org.deri.iris.api.basics.IRule;
import org.deri.iris.factory.Factory;
import org.deri.iris.storage.IRelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akiban.sql.parser.FromBaseTable;

import edu.gatech.sqltutor.rules.ISymbolicTranslationRule;
import edu.gatech.sqltutor.rules.Markers;
import edu.gatech.sqltutor.rules.TranslationPhase;
import edu.gatech.sqltutor.rules.datalog.iris.EntityLabelFormat;
import edu.gatech.sqltutor.rules.datalog.iris.IrisUtil;
import edu.gatech.sqltutor.rules.datalog.iris.RelationExtractor;
import edu.gatech.sqltutor.rules.datalog.iris.StaticRules;
import edu.gatech.sqltutor.rules.symbolic.SymbolicQueries;
import edu.gatech.sqltutor.rules.symbolic.tokens.SQLNounToken;
import edu.gatech.sqltutor.rules.symbolic.tokens.TableEntityToken;

/**
 * For a table named <em>t</em> referencing entity <em>e</em>, 
 * use <em>t</em> and <em>e</em> as potential labels.  Label names 
 * are first processed by {@link EntityLabelFormat}.
 */
public class DefaultTableLabelRule extends StandardSymbolicRule implements ISymbolicTranslationRule {
	private static final Logger _log = LoggerFactory.getLogger(DefaultTableLabelRule.class);
	
	private static final StaticRules staticRules = new StaticRules(DefaultTableLabelRule.class);
	
	private static final IPredicate PREDICATE = IrisUtil.predicate("ruleDefaultTableLabel", 3);
	private static final IQuery QUERY = Factory.BASIC.createQuery(
		literal(PREDICATE, "?table", "?singular", "?plural")
	);
	
	public DefaultTableLabelRule() {
	}
	
	@Override
	protected boolean handleResult(IRelation relation, RelationExtractor ext) {	
		final boolean debug = _log.isDebugEnabled(Markers.SYMBOLIC);
		SymbolicQueries queries = state.getQueries();
		while( ext.nextTuple() ) {
			SQLNounToken table = ext.getToken("?table");
			String singular = ext.getString("?singular"), plural = ext.getString("plural");
			table.setSingularLabel(singular);
			table.setPluralLabel(plural);
			
			TableEntityToken tableEntity = queries.getTableEntityForScope(
				(FromBaseTable)table.getAstNode(), table.getConjunctScope());
			tableEntity.setSingularLabel(singular);
			tableEntity.setPluralLabel(plural);
			
			if( debug )  {
				_log.debug(Markers.SYMBOLIC, "Updated labels for {} and {}", table, tableEntity);
			}
		}
		return true;
	}
	
	@Override
	public List<IRule> getDatalogRules() {
		return staticRules.getRules();
	}
	
	@Override
	protected IQuery getQuery() {
		return QUERY;
	}
	
	@Override
	protected EnumSet<TranslationPhase> getDefaultPhases() {
		return EnumSet.of(TranslationPhase.SQL_ANALYSIS);
	}
}
