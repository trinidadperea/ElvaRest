from django.shortcuts import render

# Create your views here.
def lista(request):
    return render(request, "designaciones/lista.html")


def nuevo(request):
    return render(request, "designaciones/nuevo.html")


def editar(request, id):
    return render(request, "designaciones/editar.html")


def detalle(request, id):
    return render(request, "designaciones/detalle.html")