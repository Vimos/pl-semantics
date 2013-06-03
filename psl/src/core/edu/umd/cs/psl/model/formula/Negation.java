/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.umd.cs.psl.model.formula;

import java.util.*;

import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.umd.cs.psl.model.argument.type.VariableTypeMap;
import edu.umd.cs.psl.model.atom.Atom;

/**
 * This class implements fuzzy negation. Note that we currently only allow the negation of singletons, i.e
 * single atoms. This is not really a restriction, because every formula can be converted into one where
 * only atoms are negated.
 * 
 * @author Matthias Broecheler
 * @author Stephen Bach
 *
 */
public class Negation implements Formula {

	/**
	 * The fuzzy singleton of which this is the negation
	 */
	private final Formula body;

	public Negation(Formula f) {
		assert f!=null;
		body = f;
	}
	
	public Formula getFormula() {
		return body;
	}
	
	@Override
	public Formula dnf() {
		if (body instanceof Atom)
			return this;
		else if (body instanceof Negation)
			return ((Negation) body).body.dnf();
		else if (body instanceof Conjunction) {
			Formula[] components = new Formula[((Conjunction) body).getNoFormulas()];
			for (int i = 0; i < components.length; i++)
				components[i] = new Negation(((Conjunction) body).get(i));
			Disjunction disjunction = new Disjunction(components);
			disjunction.conjType = ((Conjunction) body).conjType;
			disjunction.headPos = ((Conjunction) body).headPos;
			return disjunction.dnf();
		}
		else if (body instanceof Disjunction) {
			Formula[] components = new Formula[((Disjunction) body).getNoFormulas()];
			for (int i = 0; i < components.length; i++)
				components[i] = new Negation(((Disjunction) body).get(i));
			Conjunction conjunction = new Conjunction(components);
			conjunction.conjType = ((Disjunction) body).conjType;
			conjunction.headPos = ((Disjunction) body).headPos;
			return conjunction.dnf();
		}
		else if (body instanceof Rule)
			return new Negation(body.dnf()).dnf();
		else
			throw new IllegalStateException("Body of negation is unrecognized type.");
	}

	@Override
	public String toString() {
		return "!( " + body + " )";
	}
	
	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(body).toHashCode();
	}

	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		return body.equals(((Negation)oth).body);
	}

	@Override
	public Collection<Atom> getAtoms(Collection<Atom> list) {
		body.getAtoms(list);
		return list;
	}


	@Override
	public VariableTypeMap getVariables(VariableTypeMap varMap) {
		body.getVariables(varMap);
		return varMap;
	}

	
}
