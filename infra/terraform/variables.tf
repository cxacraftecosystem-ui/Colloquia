variable "aws_region" {
  description = "AWS region for the API box. Keep it near the S3 bucket + Supabase."
  type        = string
  default     = "ap-south-1"
}

variable "project" {
  description = "Name prefix for created resources."
  type        = string
  default     = "colloquia"
}

variable "instance_type" {
  description = "EC2 instance type. t3.micro is the free-tier-eligible x86 type in ap-south-1 (this account enforces free-tier-only types; t2.micro is rejected here)."
  type        = string
  default     = "t3.micro"
}

variable "root_volume_gb" {
  description = "Root EBS size (GB). Free tier covers 30 GB total across the account."
  type        = number
  default     = 16
}

variable "ssh_ingress_cidr" {
  description = "CIDR allowed to SSH. 0.0.0.0/0 works with key-only auth; restrict to YOUR.IP/32 for tighter security. The deploy workflow SSHes from GitHub runner IPs, so it needs this open (or a self-hosted runner)."
  type        = string
  default     = "0.0.0.0/0"
}
