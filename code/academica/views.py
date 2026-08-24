from django.shortcuts import render

# Create your views here.
def lista(request):
    return render(request, "academica/lista.html")


def nuevo(request):
    return render(request, "academica/nuevo.html")


def editar(request, id):
    return render(request, "academica/editar.html")


def detalle(request, id):
    return render(request, "academica/detalle.html")