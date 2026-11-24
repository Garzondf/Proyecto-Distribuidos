#!/bin/bash
# Muestra las transacciones del GT principal y del respaldo
exec tail -n 20 -F logs/gt.log logs/gt_respaldo.log
