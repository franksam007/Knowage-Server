/*
 * Knowage, Open Source Business Intelligence suite
 * Copyright (C) 2016 Engineering Ingegneria Informatica S.p.A.
 *
 * Knowage is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knowage is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.eng.spagobi.tools.dataset.common.similarity;

import java.util.Set;
import java.util.TreeSet;

public class Similarity implements Comparable<Similarity> {

	private final Set<Field> fields;
	private double coefficient;

	public Similarity() {
		this(0.0);
	}

	public Similarity(double coefficient) {
		this(new TreeSet<Field>(), coefficient);
	}

	public Similarity(Set<Field> fields, double coefficient) {
		this.fields = new TreeSet<>(fields);
		this.coefficient = coefficient;
	}

	public Set<Field> getFields() {
		return fields;
	}

	public boolean addField(Field field) {
		return fields.add(field);
	}

	public double getCoefficient() {
		return coefficient;
	}

	public double add(Similarity similarity) {
		fields.addAll(similarity.getFields());
		double average = (coefficient + similarity.coefficient) / 2;
		coefficient = SimilarityUtilities.round(average);
		return coefficient;
	}

	@Override
	public int compareTo(Similarity other) {
		int result = Double.compare(coefficient, other.coefficient);
		if (result == 0) {
			result += fields.toString().compareTo(other.toString());
		}
		return result;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(coefficient);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((fields == null) ? 0 : fields.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof Similarity)) {
			return false;
		}
		Similarity other = (Similarity) obj;
		if (Double.doubleToLongBits(coefficient) != Double.doubleToLongBits(other.coefficient)) {
			return false;
		}
		if (fields == null) {
			if (other.fields != null) {
				return false;
			}
		} else if (!fields.equals(other.fields)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Similarity [fields=");
		builder.append(fields);
		builder.append(", coefficient=");
		builder.append(coefficient);
		builder.append("]");
		return builder.toString();
	}
}
