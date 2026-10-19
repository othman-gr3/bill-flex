package com.billflex.facturation.model;

/**
 * Invoice lifecycle: BROUILLON -> EMISE -> PAYEE / EN_RETARD -> ANNULEE.
 * Allowed transitions:
 *  BROUILLON -> EMISE | ANNULEE
 *  EMISE     -> PAYEE | EN_RETARD | ANNULEE
 *  EN_RETARD -> PAYEE | ANNULEE
 *  PAYEE, ANNULEE -> (final states)
 */
public enum StatutInvoice {
    BROUILLON, EMISE, PAYEE, EN_RETARD, ANNULEE;

    public boolean peutPasserA(StatutInvoice cible) {
        return switch (this) {
            case BROUILLON -> cible == EMISE || cible == ANNULEE;
            case EMISE -> cible == PAYEE || cible == EN_RETARD || cible == ANNULEE;
            case EN_RETARD -> cible == PAYEE || cible == ANNULEE;
            case PAYEE, ANNULEE -> false;
        };
    }
}
