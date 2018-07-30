import { Element, api } from 'engine';

/**
 * This component is documented.
 */
export default class DocumentedCmp extends Element {
	/**
	 * Whether this thing is enabled.
	 *
	 * @type {boolean}
	 * @default false
	 */
	@api enabled = false;

	// private
	_something = "something";

	/**
	 * Fear is the mind-killer.
	 *
	 * @type {string}
	 */
	@api get something() {
		return this._something;
	}

	@api set something(value) {
		this.something = value;
	}

	/* private, and this isn't jsdoc */
	privateMethod() {
	}
}
