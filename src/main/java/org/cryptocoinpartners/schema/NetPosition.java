package org.cryptocoinpartners.schema;

import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

public class NetPosition extends Position {

	// JPA
	public NetPosition() {
	}

	@Override
	@ManyToOne
	@JoinColumn(name = "portfolio")
	public
	// @JoinColumn(name = "portfolio")
	Portfolio getPortfolio() {

		return portfolio;
	}

	//    @Override
	//    @OneToMany(mappedBy = "position")
	//    //, orphanRemoval = true, cascade = CascadeType.REMOVE)
	//    @OrderBy
	//    //, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
	//    public List<Fill> getFills() {
	//        if (fills == null)
	//            fills = new CopyOnWriteArrayList<Fill>();
	//        return fills;

	// }

}
