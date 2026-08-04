from django.shortcuts import render

# Create your views here.
def lista(request):
    return render(request, "licencias/lista.html")


def nuevo(request):
    return render(request, "licencias/nuevo.html")


def editar(request, id):
    return render(request, "licencias/editar.html")


def detalle(request, id):
    return render(request, "licencias/detalle.html")