/*
 *   Copyright (c) 2014 Program Analysis Group, Georgia Tech
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package edu.gatech.sqltutor.rules.symbolic;

import static edu.gatech.sqltutor.rules.datalog.iris.IrisUtil.literal;

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.EnumSet;
import java.util.Locale;

import org.deri.iris.api.basics.IQuery;
import org.deri.iris.factory.Factory;
import org.deri.iris.storage.IRelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.gatech.sqltutor.rules.DefaultPrecedence;
import edu.gatech.sqltutor.rules.ISymbolicTranslationRule;
import edu.gatech.sqltutor.rules.Markers;
import edu.gatech.sqltutor.rules.TranslationPhase;
import edu.gatech.sqltutor.rules.datalog.iris.RelationExtractor;
import edu.gatech.sqltutor.rules.datalog.iris.SymbolicPredicates;
import edu.gatech.sqltutor.rules.lang.StandardSymbolicRule;
import edu.gatech.sqltutor.rules.symbolic.tokens.ISymbolicToken;
import edu.gatech.sqltutor.rules.symbolic.tokens.LiteralToken;
import edu.gatech.sqltutor.rules.symbolic.tokens.NumberToken;

public class NumberLiteralRule 
		extends StandardSymbolicRule implements ISymbolicTranslationRule {
	private static final Logger _log = LoggerFactory.getLogger(NumberLiteralRule.class);
	
	private static final IQuery QUERY = Factory.BASIC.createQuery(
		literal(SymbolicPredicates.parentOf, "?parent", "?token", "?pos"),
		literal(SymbolicPredicates.type, "?token", SymbolicType.NUMBER)
	);

	public NumberLiteralRule() {
		super(DefaultPrecedence.LOWERING);
	}
	
	public NumberLiteralRule(int precedence) {
		super(precedence);
	}
	
	@Override
	protected boolean handleResult(IRelation relation, RelationExtractor ext) {
		boolean applied = false;
		
		while( ext.nextTuple() ) {
			ISymbolicToken parent = ext.getToken("?parent");
			NumberToken numberToken = (NumberToken)ext.getToken("?token");
			NumberFormat numberFormat;
			switch( numberToken.getValueType() ) {
			case DOLLARS:
				numberFormat = new CurrencyFormatWrapper();
				break;
			default:
				_log.warn("Unhandled numeric type in token: {}", numberToken);
				// fall-through
			case NUMBER:
			case UNKNOWN:
				numberFormat = NumberFormat.getNumberInstance(Locale.US);
				break;
			}
			
			String expression = numberFormat.format(numberToken.getNumber());
			LiteralToken literal = new LiteralToken(expression, numberToken.getPartOfSpeech());
			
			SymbolicUtil.replaceChild(parent, numberToken, literal);
			_log.debug(Markers.SYMBOLIC, "Replaced {} with {}", numberToken, literal);
			applied = true;
		}
		return applied;
	}
	
	@Override
	protected IQuery getQuery() { return QUERY; }
	
	@Override
	protected EnumSet<TranslationPhase> getDefaultPhases() {
		return EnumSet.of(TranslationPhase.LOWERING);
	}
	
	private static class CurrencyFormatWrapper extends NumberFormat {
		private static final long serialVersionUID = 1L;
		
		private NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
		public CurrencyFormatWrapper() {
		}


		private StringBuffer checkFormat(StringBuffer toAppendTo) {
			int size = toAppendTo.length();
			for( int i = size - 1; i >= 0; --i ) {
				char charAt = toAppendTo.charAt(i);
				// scanning for all 0s after the dot
				if( charAt == '0' )
					continue;
				if( charAt == '.' ) {
					toAppendTo.setLength(i);
					break;
				}
				if( charAt == '$' || charAt == ',' || (charAt >= '1' && charAt <= '9') )
					break;
			}
			return toAppendTo;
		}

		@Override
		public StringBuffer format(double number, StringBuffer toAppendTo,
				FieldPosition pos) {
			return checkFormat(format.format(number, toAppendTo, pos));
		}

		@Override
		public StringBuffer format(long number, StringBuffer toAppendTo,
				FieldPosition pos) {
			return checkFormat(format.format(number, toAppendTo, pos));
		}

		@Override
		public Number parse(String source, ParsePosition parsePosition) {
			return format.parse(source, parsePosition);
		}
	}
}
