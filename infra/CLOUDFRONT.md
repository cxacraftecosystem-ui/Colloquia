# CloudFront in front of the API box

**Already provisioned** by `infra/terraform/main.tf` (resource `aws_cloudfront_distribution.api`):

- Distribution: `d2nrls693zgomu.cloudfront.net` (HTTPS + IPv6, terraform output `cloudfront_domain`).
- Origin: the EIP's AWS hostname `ec2-13-127-140-238.ap-south-1.compute.amazonaws.com` on **HTTP port 80** (CloudFront terminates HTTPS; origin stays plain HTTP).
- API behaviour: all methods allowed, **nothing cached** (min/default/max TTL = 0), forwards all headers/query/cookies, `redirect-to-https`.
- The Android app points at `https://d2nrls693zgomu.cloudfront.net/api/`.

Re-create/destroy it with `terraform apply` / `terraform destroy` in `infra/terraform`.

---

## Doing it manually in the AWS Console (equivalent steps)

CloudFront needs a **domain** origin, not a bare IP. Use the box's AWS hostname:
`ec2-<dash-separated-EIP>.<region>.compute.amazonaws.com` (here `ec2-13-127-140-238.ap-south-1.compute.amazonaws.com`).

1. **CloudFront → Create distribution.**
2. **Origin domain:** paste the EC2 hostname above (not the IP).
3. **Protocol:** *HTTP only* (the origin has no TLS cert). Origin HTTP port `80`.
4. **Origin read timeout:** 60s (so long transcription requests don't 504).
5. **Default cache behavior:**
   - Viewer protocol policy: **Redirect HTTP to HTTPS**.
   - Allowed HTTP methods: **GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE**.
   - Cache policy: **CachingDisabled** (it's an API).
   - Origin request policy: **AllViewer** (forward all headers/query/cookies).
6. **Settings:** Price class *Use only North America and Europe* (cheapest), **IPv6 = On**, Default SSL certificate (`*.cloudfront.net`).
7. **Create.** Wait ~3–5 min for "Deployed".
8. Test: `curl https://<dxxxx>.cloudfront.net/health` → `{"status":"ok"}`.
9. Point the app at `https://<dxxxx>.cloudfront.net/api/` (already done for the terraform-created one).

### Notes
- The EC2 security group already allows HTTP/80 from anywhere, which is what CloudFront's origin fetch needs. You may optionally restrict origin access later with a custom header + WAF.
- For a custom domain (e.g. `api.colloquia.app`): add it under *Alternate domain names (CNAMEs)*, attach an ACM cert **in us-east-1**, then CNAME your domain to the distribution.
