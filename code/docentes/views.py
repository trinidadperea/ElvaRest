from django.shortcuts import render

# Create your views here.
def lista(request):
    return render(request, "docentes/lista.html")


def nuevo(request):
    return render(request, "docentes/nuevo.html")


def editar(request, id):
    return render(request, "docentes/editar.html")


def detalle(request, id):
    return render(request, "docentes/detalle.html")