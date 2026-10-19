INSERT INTO clients (nom, email, numero_tva, mode_paiement, statut) VALUES
('Atlas Tech SARL',   'contact@atlastech.ma',   'MA12345678', 'VIREMENT',    'ACTIF'),
('Sahara Digital',    'billing@sahara.ma',      'MA87654321', 'CARTE',       'ACTIF'),
('Maghreb Logistics', 'finance@maghreb-log.ma', 'MA11223344', 'PRELEVEMENT', 'ACTIF');

INSERT INTO payments (client_id, invoice_id, date_paiement, montant, moyen, statut) VALUES
(1, 1, '2026-10-02', 118.80, 'VIREMENT',    'PAYE'),
(2, 2, '2026-10-15', 358.80, 'CARTE',       'EN_ATTENTE'),
(3, 3, '2026-10-16',  34.80, 'PRELEVEMENT', 'EN_ATTENTE');
