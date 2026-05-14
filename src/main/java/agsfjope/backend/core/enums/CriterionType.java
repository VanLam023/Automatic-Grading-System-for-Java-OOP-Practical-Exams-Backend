package agsfjope.backend.core.enums;

/**
 * Types of structural OOP criteria that can be checked
 * by JavaParser or Java Reflection without running the code.
 *
 * <ul>
 *   <li>JavaParser-based: CLASS_EXISTS, INTERFACE_EXISTS, FIELD_CHECK,
 *       GETTER_SETTER, EXTENDS_CHECK, IMPLEMENTS_CHECK, NAMING_CONVENTION</li>
 *   <li>Reflection-based (fallback to JavaParser if .jar missing):
 *       CONSTRUCTOR_CHECK, METHOD_SIGNATURE</li>
 * </ul>
 */
public enum CriterionType {

    /** Class with the given name exists in student's source. */
    CLASS_EXISTS,

    /** Interface with the given name exists in student's source. */
    INTERFACE_EXISTS,

    /** A field exists with the expected name, type, and/or access modifier. */
    FIELD_CHECK,

    /** A constructor with the exact parameter type list exists. */
    CONSTRUCTOR_CHECK,

    /** A method exists with the correct name, return type, and parameter list. */
    METHOD_SIGNATURE,

    /** Both getter and setter for the given field name exist. */
    GETTER_SETTER,

    /** The class extends the specified parent class. */
    EXTENDS_CHECK,

    /** The class implements the specified interface. */
    IMPLEMENTS_CHECK,

    /** All field names follow camelCase convention. */
    NAMING_CONVENTION
}
