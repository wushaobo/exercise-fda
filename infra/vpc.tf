data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "fds" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "fds-${var.env}" }
}

resource "aws_subnet" "public" {
  count                   = var.az_count
  vpc_id                  = aws_vpc.fds.id
  cidr_block              = cidrsubnet(aws_vpc.fds.cidr_block, 8, count.index)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = { Name = "fds-${var.env}-public-${count.index}" }
}

resource "aws_subnet" "private" {
  count             = var.az_count
  vpc_id            = aws_vpc.fds.id
  cidr_block        = cidrsubnet(aws_vpc.fds.cidr_block, 8, count.index + var.az_count)
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = { Name = "fds-${var.env}-private-${count.index}" }
}

resource "aws_internet_gateway" "fds" {
  vpc_id = aws_vpc.fds.id
  tags   = { Name = "fds-${var.env}" }
}

resource "aws_eip" "nat" {
  count  = var.az_count
  domain = "vpc"
}

resource "aws_nat_gateway" "fds" {
  count         = var.az_count
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id
  tags          = { Name = "fds-${var.env}-${count.index}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.fds.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.fds.id
  }
  tags = { Name = "fds-${var.env}-public" }
}

resource "aws_route_table_association" "public" {
  count          = var.az_count
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  count  = var.az_count
  vpc_id = aws_vpc.fds.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.fds[count.index].id
  }
  tags = { Name = "fds-${var.env}-private-${count.index}" }
}

resource "aws_route_table_association" "private" {
  count          = var.az_count
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}
