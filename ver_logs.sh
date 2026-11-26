#!/bin/bash
# Muestra en vivo los logs de todos los procesos del negocio (GT, RMC, Cancelaciones y respaldos)
exec tail -n 5 -F logs/gt.log logs/gt_respaldo.log logs/rmc.log logs/rmc_respaldo.log logs/cancelaciones.log
