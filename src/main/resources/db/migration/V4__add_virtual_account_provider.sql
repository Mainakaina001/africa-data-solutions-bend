-- Existing virtual accounts were all issued via Billstack; new ones are
-- issued via PaymentPoint going forward (see PaymentPointClient). Both
-- providers' webhooks stay live during the transition.
ALTER TABLE virtual_accounts ADD COLUMN provider TEXT NOT NULL DEFAULT 'billstack';
