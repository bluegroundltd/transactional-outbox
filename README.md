# Transactional Outbox Library

[![Build](https://github.com/bluegroundltd/transactional-outbox/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/bluegroundltd/transactional-outbox/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Transactional Outbox is a library that provides a simple way to implement
the [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html) in your
application, developed by Blueground.

API Docs: https://bluegroundltd.github.io/transactional-outbox/

## Core Implementation

Transactional Outbox Core is a library that provides a framework-agnostic way to implement the Transactional Outbox Pattern.

[More information here](./core/README.md)

## Spring - Postgres Implementation

Transactional Outbox Spring is a reference implementation of the `core` library interfaces, using Spring framework and Postgres,
both as a storage and a locks' provider.

[More information here](./spring/README.md)

## Maintainers

The core maintainer of this project are:

* [Chris Aslanoglou](https://github.com/chris-asl)
* [Apostolos Kiraleos](https://github.com/kiraleos)
* [Thanasis Polydoros](https://github.com/ippokratoys)
* [Grigoris Balaskas](https://github.com/gregBal)
* [Thanasis Karampatsis](https://github.com/tkarampatsisBG)
