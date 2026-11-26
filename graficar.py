#!/usr/bin/env python3
# Genera los graficos del informe: arquitectura base vs escalable segun el numero de clientes.
# Uso: python3 graficar.py resultados/base_FECHA resultados/escalable_FECHA [carpeta_salida]
# Requiere: pip install matplotlib
import csv
import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt


def leer(carpeta, archivo):
    with open(os.path.join(carpeta, archivo), newline="") as f:
        return list(csv.DictReader(f))


def tiempos(carpeta):
    # {(operacion, clientes): (promedio, desviacion)}
    d = {}
    for r in leer(carpeta, "resumen_velocidad.csv"):
        d[(r["Operacion"], int(r["Clientes"]))] = (float(r["Promedio_ms"]), float(r["DesvStd_ms"]))
    return d


def tpm_gt(carpeta):
    # {(operacion, clientes): tpm medido en el GT}
    d = {}
    for r in leer(carpeta, "resumen_tpm_gt.csv"):
        d[(r["Operacion"], int(r["Clientes"]))] = float(r["TPM_GT"])
    return d


def conteos(carpeta):
    # {(operacion, clientes): (total, exitosas)}
    d = {}
    for r in leer(carpeta, "resumen_velocidad.csv"):
        d[(r["Operacion"], int(r["Clientes"]))] = (int(r["Total"]), int(r["Exitosas"]))
    return d


def tabla_resultados(salida, clientes, arquitecturas):
    # arquitecturas: lista de (nombre, tiempos, tpm, conteos). Deja la tabla del informe en .md y .csv
    cab = ["Clientes", "Arquitectura", "Consulta prom (ms)", "Consulta desv (ms)", "Reserva prom (ms)",
           "Reserva desv (ms)", "TPM consultas", "TPM reservas", "Reservas exitosas / total"]
    filas = []
    for n in clientes:
        for nombre, t, g, c in arquitecturas:
            cons, res = t.get(("consulta", n)), t.get(("reserva", n))
            cr = c.get(("reserva", n))
            filas.append([
                n, nombre,
                "%.1f" % cons[0] if cons else "-", "%.1f" % cons[1] if cons else "-",
                "%.1f" % res[0] if res else "-", "%.1f" % res[1] if res else "-",
                "%.0f" % g[("consultas", n)] if ("consultas", n) in g else "-",
                "%.0f" % g[("reservas", n)] if ("reservas", n) in g else "-",
                "%d / %d" % (cr[1], cr[0]) if cr else "-"])
    with open(os.path.join(salida, "tabla_resultados.csv"), "w", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(cab)
        w.writerows(filas)
    with open(os.path.join(salida, "tabla_resultados.md"), "w") as f:
        f.write("| " + " | ".join(cab) + " |\n")
        f.write("|" + "---|" * len(cab) + "\n")
        for fila in filas:
            f.write("| " + " | ".join(str(x) for x in fila) + " |\n")


def serie(d, op, clientes):
    return [d.get((op, n)) for n in clientes]


def grafico_lineas(ax, clientes, base, esc, titulo, ylabel, con_error=False):
    for nombre, datos, marca in (("Arquitectura base", base, "o-"), ("Arquitectura escalable", esc, "s--")):
        xs = [n for n, v in zip(clientes, datos) if v is not None]
        if con_error:
            ys = [v[0] for v in datos if v is not None]
            er = [v[1] for v in datos if v is not None]
            ax.errorbar(xs, ys, yerr=er, fmt=marca, capsize=4, label=nombre)
        else:
            ys = [v for v in datos if v is not None]
            ax.plot(xs, ys, marca, label=nombre)
    ax.set_title(titulo)
    ax.set_xlabel("Clientes simultaneos")
    ax.set_ylabel(ylabel)
    ax.set_xticks(clientes)
    ax.grid(True, alpha=0.3)
    ax.legend()


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    dir_base, dir_esc = sys.argv[1], sys.argv[2]
    salida = sys.argv[3] if len(sys.argv) > 3 else "graficos"
    os.makedirs(salida, exist_ok=True)

    clientes = [3, 5, 8]
    t_base, t_esc = tiempos(dir_base), tiempos(dir_esc)
    g_base, g_esc = tpm_gt(dir_base), tpm_gt(dir_esc)

    # (archivo, titulo, eje y, datos base, datos escalable, con barras de error)
    graficos = [
        ("tiempo_consulta", "Tiempo promedio de respuesta: consultas", "Milisegundos (promedio y desv. estandar)",
         serie(t_base, "consulta", clientes), serie(t_esc, "consulta", clientes), True),
        ("tiempo_reserva", "Tiempo promedio de respuesta: reservas", "Milisegundos (promedio y desv. estandar)",
         serie(t_base, "reserva", clientes), serie(t_esc, "reserva", clientes), True),
        ("tpm_consulta", "Consultas procesadas por minuto (GT)", "Transacciones por minuto",
         serie(g_base, "consultas", clientes), serie(g_esc, "consultas", clientes), False),
        ("tpm_reserva", "Reservas procesadas por minuto (GT)", "Transacciones por minuto",
         serie(g_base, "reservas", clientes), serie(g_esc, "reservas", clientes), False),
    ]

    # un grafico por archivo y uno con los cuatro juntos
    fig_todos, ejes = plt.subplots(2, 2, figsize=(12, 9))
    for (nombre, titulo, ylabel, b, e, err), ax_todos in zip(graficos, ejes.flat):
        fig, ax = plt.subplots(figsize=(6.5, 4.5))
        grafico_lineas(ax, clientes, b, e, titulo, ylabel, err)
        fig.tight_layout()
        fig.savefig(os.path.join(salida, nombre + ".png"), dpi=150)
        plt.close(fig)
        grafico_lineas(ax_todos, clientes, b, e, titulo, ylabel, err)
    fig_todos.tight_layout()
    fig_todos.savefig(os.path.join(salida, "comparacion_completa.png"), dpi=150)

    # tabla con el formato de la Tabla 1 del enunciado (para pegar en el informe)
    tabla_resultados(salida, clientes, [
        ("Base", t_base, g_base, conteos(dir_base)),
        ("Escalable", t_esc, g_esc, conteos(dir_esc))])

    # tabla comparativa con el porcentaje de mejora
    with open(os.path.join(salida, "tabla_comparativa.csv"), "w", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["Metrica", "Clientes", "Base", "Escalable", "Cambio_%"])
        for nombre, titulo, ylabel, b, e, err in graficos:
            for n, vb, ve in zip(clientes, b, e):
                if vb is None or ve is None:
                    continue
                vb = vb[0] if err else vb
                ve = ve[0] if err else ve
                cambio = (ve - vb) / vb * 100 if vb else 0
                w.writerow([titulo, n, round(vb, 2), round(ve, 2), round(cambio, 1)])
    print("Graficos y tablas guardados en", salida)


if __name__ == "__main__":
    main()
