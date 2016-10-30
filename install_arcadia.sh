#!/bin/bash

rm -rf Assets/Arcadia
git clone https://github.com/arcadia-unity/Arcadia.git Assets/Arcadia
cd Assets/Arcadia; git checkout develop
