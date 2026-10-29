package com.fasterxml.jackson.module.blackbird.deser.isolated;

// These fixtures have their own package: on JDK 9 to 13 Blackbird reuses a
// `$$JacksonBlackbirdAccess` class it finds through the child loader, and other tests
// leave one in the parent loader for their own packages
public enum Token { YES }
