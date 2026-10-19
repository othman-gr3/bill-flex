INSERT INTO plans (nom, prix_mensuel, quota_utilisateurs, actif) VALUES
('Starter',    29.00,   5, TRUE),
('Business',   99.00,  25, TRUE),
('Enterprise', 299.00, 200, TRUE);

INSERT INTO subscriptions (plan_id, client_id, date_debut, date_fin, prochaine_echeance, statut) VALUES
(2, 1, '2026-09-01', NULL, '2026-11-01', 'ACTIVE'),
(3, 2, '2026-09-15', NULL, '2026-11-15', 'ACTIVE'),
(1, 3, '2026-10-01', NULL, '2026-11-01', 'ACTIVE');
