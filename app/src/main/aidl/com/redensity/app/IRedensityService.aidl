package com.redensity.app;

interface IRedensityService {
    String exec(in String[] cmd) = 1;
    void destroy() = 16777114;
}

