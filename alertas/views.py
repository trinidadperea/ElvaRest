from django.shortcuts import render

# Create your views here.
def lista(request):
    return render(request, "alertas/lista.html")


def nuevo(request):
    return render(request, "alertas/nuevo.html")


def editar(request, id):
    return render(request, "alertas/editar.html")


def detalle(request, id):
    return render(request, "alertas/detalle.html")