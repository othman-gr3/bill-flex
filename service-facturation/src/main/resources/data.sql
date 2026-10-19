INSERT INTO invoices (subscription_id, client_id, periode, montant_ht, montant_ttc, echeance, statut) VALUES
(1, 1, '2026-09',  99.00, 118.80, '2026-10-05', 'PAYEE'),
(2, 2, '2026-10', 299.00, 358.80, '2026-11-05', 'EMISE'),
(3, 3, '2026-10',  29.00,  34.80, '2026-11-05', 'BROUILLON');

INSERT INTO invoice_lines (invoice_id, libelle, quantite, prix_unitaire) VALUES
(1, 'Abonnement Business - septembre 2026',   1,  99.00),
(2, 'Abonnement Enterprise - octobre 2026',   1, 299.00),
(3, 'Abonnement Starter - octobre 2026',      1,  29.00);
