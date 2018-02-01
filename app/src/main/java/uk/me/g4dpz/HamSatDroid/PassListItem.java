/**
 * 
 */
package uk.me.g4dpz.HamSatDroid;

import java.io.Serializable;

public class PassListItem implements Serializable {

	private static final long serialVersionUID = -8471994139396007828L;

	private String when;
	private String where;

	public PassListItem(final String when, final String where) {
		setWhen(when);
		setWhere(where);
	}

	public final String getWhen() {
		return when;
	}

	public final void setWhen(final String when) {
		this.when = when;
	}

	public final String getWhere() {
		return where;
	}

	public final void setWhere(final String where) {
		this.where = where;
	}

}
