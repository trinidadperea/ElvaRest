from django.shortcuts import render

# Create your views here.
def lista(request):
    return render(request, "usuarios/lista.html")


def nuevo(request):
    return render(request, "usuarios/nuevo.html")


def editar(request, id):
    return render(request, "usuarios/editar.html")


def detalle(request, id):
    return render(request, "usuarios/detalle.html")