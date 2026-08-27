-- V2's sme_plug_plan_id values (1-6) were explicitly-flagged placeholders that
-- don't exist in SME Plug's real catalog, causing every purchase to fail with
-- "Invalid data plan selected". These are the real MTN "Share - Monthly" plan
-- ids from SME Plug's live /data/plans response (network id 1 = MTN).
-- Note: data_plans.price is no longer used to bill purchases (see
-- DataService.purchaseData, which always reads the live SME Plug price) — it
-- only backs the plan listing as a fallback if that live call fails, so it's
-- refreshed here to stay close to the real current cost.

UPDATE data_plans SET sme_plug_plan_id = 172, price = 290  WHERE plan_code = 'MTN_SME_500MB';
UPDATE data_plans SET sme_plug_plan_id = 173, price = 550  WHERE plan_code = 'MTN_SME_1GB';
UPDATE data_plans SET sme_plug_plan_id = 174, price = 1090 WHERE plan_code = 'MTN_SME_2GB';
UPDATE data_plans SET sme_plug_plan_id = 175, price = 1590 WHERE plan_code = 'MTN_SME_3GB';
UPDATE data_plans SET sme_plug_plan_id = 176, price = 2450 WHERE plan_code = 'MTN_SME_5GB';
UPDATE data_plans SET sme_plug_plan_id = 522, price = 4900 WHERE plan_code = 'MTN_SME_10GB';
