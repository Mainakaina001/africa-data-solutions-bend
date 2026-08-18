-- Sample MTN SME data plans. Mirrors backend/prisma/seed.ts.
-- NOTE: sme_plug_plan_id values are placeholders — replace with the real
-- plan IDs from the SME Plug API before going live.

INSERT INTO data_plans (id, network, network_id, sme_plug_plan_id, plan_code, plan_name, data_amount, price, validity, plan_type, description)
VALUES
    (gen_random_uuid(), 'MTN', 1, 1, 'MTN_SME_500MB', '500MB MTN SME', '500MB', 150, '30 days', 'SME', 'MTN SME 500MB valid for 30 days'),
    (gen_random_uuid(), 'MTN', 1, 2, 'MTN_SME_1GB',   '1GB MTN SME',   '1GB',   280, '30 days', 'SME', 'MTN SME 1GB valid for 30 days'),
    (gen_random_uuid(), 'MTN', 1, 3, 'MTN_SME_2GB',   '2GB MTN SME',   '2GB',   560, '30 days', 'SME', 'MTN SME 2GB valid for 30 days'),
    (gen_random_uuid(), 'MTN', 1, 4, 'MTN_SME_3GB',   '3GB MTN SME',   '3GB',   840, '30 days', 'SME', 'MTN SME 3GB valid for 30 days'),
    (gen_random_uuid(), 'MTN', 1, 5, 'MTN_SME_5GB',   '5GB MTN SME',   '5GB',  1400, '30 days', 'SME', 'MTN SME 5GB valid for 30 days'),
    (gen_random_uuid(), 'MTN', 1, 6, 'MTN_SME_10GB',  '10GB MTN SME',  '10GB', 2800, '30 days', 'SME', 'MTN SME 10GB valid for 30 days')
ON CONFLICT (plan_code) DO NOTHING;
