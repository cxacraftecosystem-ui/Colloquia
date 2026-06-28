output "api_public_ip" {
  description = "Stable Elastic IP of the API box. Set EC2_HOST to this and point the app at http://<ip>/api/."
  value       = aws_eip.api.public_ip
}

output "ssh_key_path" {
  description = "Path to the generated deploy private key (also paste its contents into the EC2_SSH_KEY secret)."
  value       = local_sensitive_file.deploy_pem.filename
}

output "ssh_command" {
  description = "Convenience SSH command."
  value       = "ssh -i ${local_sensitive_file.deploy_pem.filename} ubuntu@${aws_eip.api.public_ip}"
}

output "cloudfront_domain" {
  description = "HTTPS + IPv6 API domain. Point the app at https://<this>/api/."
  value       = aws_cloudfront_distribution.api.domain_name
}
